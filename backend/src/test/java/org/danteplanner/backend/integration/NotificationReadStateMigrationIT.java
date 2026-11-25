package org.danteplanner.backend.integration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.Column;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationReadStateMigrationIT extends ExclusiveDatabaseSupport {

    private static final String INDEX = "idx_notifications_user_read_at";
    private static final String DELETED_AT = "2026-01-03 04:05:06.123456";
    private static final String READ_AT = "2026-01-02 03:04:05.654321";
    private static final Pattern NOTIFICATIONS_SQL =
            Pattern.compile("\\b(?:INTO|FROM|JOIN|UPDATE)\\s+notifications\\b(?:(?!\"\"\").)*", Pattern.DOTALL);
    private static final Pattern RETIRED_COLUMN =
            Pattern.compile("`read`|\\bread\\b(?!_)|(?<!u\\.)\\bdeleted_at\\b");

    @Autowired private UserRepository userRepository;
    @Autowired private DataSource dataSource;

    private JdbcTemplate jdbc;
    private Long userId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM notifications");
        userId = TestDataFactory.createTestUser(userRepository, "read-state@example.com").getId();
        dropIndex();
    }

    @AfterEach
    void restoreSchema() {
        dropIndex();
        applyMigration();
    }

    @Test
    void migration_WhenRowsWereDeletedReadOrNeither_MarksOnlyTheDeletedUnreadOnesAndASecondRunChangesNothing() {
        Long deleted = row(DELETED_AT, null);
        Long both = row(DELETED_AT, READ_AT);
        Long read = row(null, READ_AT);
        Long neither = row(null, null);

        assertThat(applyMigration()).isEqualTo(1);

        assertThat(readAt(deleted)).isEqualTo(DELETED_AT);
        assertThat(readAt(both)).isEqualTo(READ_AT);
        assertThat(readAt(read)).isEqualTo(READ_AT);
        assertThat(readAt(neither)).isNull();

        dropIndex();
        assertThat(applyMigration()).isZero();

        assertThat(Stream.of(deleted, both, read, neither).map(this::readAt))
                .containsExactly(DELETED_AT, READ_AT, READ_AT, null);
    }

    @Test
    void migration_WhenItEnds_HasTheIndexAndLeavesTheSessionsIsolationAndNoTemporaryTable() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            JdbcTemplate session = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            session.execute("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ");

            statements().forEach(session::update);
            statements().stream().filter(statement -> !statement.startsWith("ALTER")).forEach(session::update);

            assertThat(session.queryForObject("SELECT @@SESSION.transaction_isolation", String.class))
                    .isEqualTo("REPEATABLE-READ");
            return null;
        });

        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'notifications' AND index_name = ? "
                + "ORDER BY seq_in_index", String.class, INDEX))
                .containsExactly("user_id", "read_at", "created_at");
    }

    @Test
    void migration_WhenARowIsReadAfterItWasSelected_KeepsThatReadAt() {
        Long deleted = row(DELETED_AT, null);

        inMigrationSession(session -> statements().forEach(statement -> {
            if (statement.startsWith("UPDATE")) {
                jdbc.update("UPDATE notifications SET read_at = ? WHERE id = ?", READ_AT, deleted);
            }
            session.update(statement);
        }));

        assertThat(readAt(deleted)).isEqualTo(READ_AT);
    }

    @Test
    void migration_WhenASessionHoldsARowItHasNoReasonToChange_WritesTheOthersWithoutWaiting() {
        Long held = row(null, null);
        Long deleted = row(DELETED_AT, null);

        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status -> {
            jdbc.queryForList("SELECT id FROM notifications WHERE id = ? FOR UPDATE", held);
            try {
                Hang.get(CompletableFuture.runAsync(() -> inMigrationSession(session -> {
                    session.execute("SET SESSION innodb_lock_wait_timeout = 1");
                    statements().stream().filter(statement -> !statement.startsWith("ALTER"))
                            .forEach(session::update);
                })), "the migration beside a held row");
            } catch (ExecutionException e) {
                throw new AssertionError(e.getCause());
            }
        });

        assertThat(readAt(deleted)).isEqualTo(DELETED_AT);
    }

    @Test
    void mainSources_WhenScanned_NameNeitherRetiredColumnOfNotifications() throws IOException {
        assertThat(Arrays.stream(Notification.class.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Column.class))
                .map(field -> field.getAnnotation(Column.class).name().replace("`", "")))
                .contains("read_at", "public_id")
                .doesNotContain("read", "deleted_at");
        assertThat(RETIRED_COLUMN.matcher("INTO notifications (deleted_at)").find()).isTrue();
        assertThat(RETIRED_COLUMN.matcher("UPDATE notifications SET `read` = TRUE").find()).isTrue();
        assertThat(RETIRED_COLUMN.matcher("WHERE u.deleted_at IS NULL AND read_at IS NULL").find()).isFalse();
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            List<String> statements = sources.filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> NOTIFICATIONS_SQL.matcher(content(path)).results())
                    .map(match -> match.group())
                    .toList();
            assertThat(statements).hasSizeGreaterThanOrEqualTo(3)
                    .noneMatch(statement -> RETIRED_COLUMN.matcher(statement).find());
        }
    }

    private static String content(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Long row(@Nullable String deletedAt, @Nullable String readAt) {
        String publicId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO notifications (user_id, content_id, notification_type, public_id, deleted_at, "
                + "read_at) VALUES (?, ?, 'COMMENT_RECEIVED', UUID_TO_BIN(?), ?, ?)",
                userId, publicId, publicId, deletedAt, readAt);
        return jdbc.queryForObject("SELECT id FROM notifications WHERE public_id = UUID_TO_BIN(?)",
                Long.class, publicId);
    }

    private @Nullable String readAt(Long id) {
        return jdbc.queryForObject("SELECT CAST(read_at AS CHAR) FROM notifications WHERE id = ?", String.class, id);
    }

    private void dropIndex() {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() "
                + "AND table_name = 'notifications' AND index_name = ?", Integer.class, INDEX) > 0) {
            jdbc.execute("ALTER TABLE notifications DROP INDEX " + INDEX);
        }
    }

    private int applyMigration() {
        int[] written = new int[1];
        inMigrationSession(session -> statements().forEach(statement -> {
            int affected = session.update(statement);
            written[0] += statement.startsWith("UPDATE") ? affected : 0;
        }));
        return written[0];
    }

    private void inMigrationSession(Consumer<JdbcTemplate> work) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            JdbcTemplate session = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            try {
                work.accept(session);
            } finally {
                session.execute("DROP TEMPORARY TABLE IF EXISTS dismissed_notification");
                session.execute("SET SESSION transaction_isolation = DEFAULT");
                session.execute("SET SESSION innodb_lock_wait_timeout = DEFAULT");
            }
            return null;
        });
    }

    private static List<String> statements() {
        try {
            return Arrays.stream(new ClassPathResource("db/migration/V070__notification_read_state.sql")
                            .getContentAsString(StandardCharsets.UTF_8).split(";\\s*(?:\\n\\n|$)"))
                    .map(String::strip)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

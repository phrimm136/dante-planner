package org.danteplanner.backend.integration;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.danteplanner.backend.config.TestConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Settings backfill seam: the migration that splits the sync preference must leave a legacy row
 * that never answered the first-login prompt as sync-off with the choice still outstanding, and a
 * row that did answer with its preference intact and the choice recorded — after which
 * {@code sync_enabled} carries no nulls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class SettingsBackfillMigrationIT extends SharedMySqlContainerSupport {

    /**
     * The migration's statements are rebound onto this table, which stands in for
     * {@code user_settings} as it was before the split. Rebinding is what keeps the shared
     * database's already-migrated table — whose {@code sync_enabled} is now non-null, so no legacy
     * row can be staged in it — out of the test's way.
     */
    private static final String LEGACY_TABLE = "user_settings_sync_choice_legacy";

    private static final long NEVER_CHOSE = 1L;
    private static final long CHOSE_SYNC_ON = 2L;
    private static final long CHOSE_SYNC_OFF = 3L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Stages the three pre-split shapes a settings row could hold. Only the columns the migration
     * reads or writes are carried; the rest of {@code user_settings} is inert to it.
     */
    private void stageLegacyRows() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + LEGACY_TABLE);
        jdbcTemplate.execute("CREATE TABLE " + LEGACY_TABLE + " ("
                + "user_id BIGINT NOT NULL PRIMARY KEY, "
                + "sync_enabled BOOLEAN NULL, "
                + "notify_comments BOOLEAN NOT NULL DEFAULT TRUE)");
        jdbcTemplate.update("INSERT INTO " + LEGACY_TABLE
                        + " (user_id, sync_enabled) VALUES (?, NULL), (?, TRUE), (?, FALSE)",
                NEVER_CHOSE, CHOSE_SYNC_ON, CHOSE_SYNC_OFF);
    }

    private void applySyncChoiceMigration() throws Exception {
        Resource[] migrations = new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*__explicit_sync_choice.sql");
        assertThat(migrations)
                .as("the sync-choice migration must exist")
                .isNotEmpty();

        String sql = new String(migrations[0].getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String rebound = Arrays.stream(sql.split("\n"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"))
                .replace("user_settings", LEGACY_TABLE);

        Arrays.stream(rebound.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .forEach(jdbcTemplate::execute);
    }

    private Boolean flag(long userId, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM " + LEGACY_TABLE + " WHERE user_id = ?",
                Boolean.class, userId);
    }

    private String nullabilityOfSyncEnabled() {
        return jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? "
                        + "AND column_name = 'sync_enabled'",
                String.class, LEGACY_TABLE);
    }

    @Test
    void syncChoiceBackfill_WhenLegacyRows_SplitsPreferenceFromChoiceMade() throws Exception {
        stageLegacyRows();

        applySyncChoiceMigration();

        assertThat(flag(NEVER_CHOSE, "sync_enabled"))
                .as("a row that never answered the prompt collapses to sync-off")
                .isFalse();
        assertThat(flag(NEVER_CHOSE, "sync_choice_made"))
                .as("and its choice is still outstanding")
                .isFalse();

        assertThat(flag(CHOSE_SYNC_ON, "sync_enabled"))
                .as("a row that chose sync-on keeps its preference")
                .isTrue();
        assertThat(flag(CHOSE_SYNC_ON, "sync_choice_made"))
                .as("and its choice is recorded")
                .isTrue();

        assertThat(flag(CHOSE_SYNC_OFF, "sync_enabled"))
                .as("a row that chose sync-off keeps its preference")
                .isFalse();
        assertThat(flag(CHOSE_SYNC_OFF, "sync_choice_made"))
                .as("and its choice is recorded, which is what separates it from a legacy row")
                .isTrue();

        assertThat(nullabilityOfSyncEnabled())
                .as("the migration leaves no null sync preference behind")
                .isEqualTo("NO");
    }
}

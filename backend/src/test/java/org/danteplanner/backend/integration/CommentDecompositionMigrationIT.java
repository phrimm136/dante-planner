package org.danteplanner.backend.integration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.StoredComments;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentDecompositionMigrationIT extends ExclusiveDatabaseSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PlannerRepository plannerRepository;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private DataSource dataSource;

    private JdbcTemplate jdbc;
    private User author;
    private StoredPlanner planner;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        emptyPlannerTables(jdbc);
        author = TestDataFactory.createTestUser(userRepository, "decomposition-author@example.com");
        planner = TestDataFactory.createTestPlanner(plannerRepository, author, true);
    }

    @AfterEach
    void restoreTables() {
        applyMigration();
    }

    @Test
    void migration_WhenRowsHaveTheOldShape_CopiesEachIntoBothTables() {
        oldShapeRow("plain", null, null, true, 0);
        oldShapeRow("edited", "2026-01-02 03:04:05", null, false, 7);
        oldShapeRow("", null, "2026-01-03 04:05:06", true, 1);
        dropNewTables();

        applyMigration();

        assertThat(newShape()).isEqualTo(oldShape()).hasSize(3);
    }

    @Test
    void migration_WhenRunTwice_ChangesNothingAlreadyWritten() {
        Long id = oldShapeRow("before", null, null, true, 1);
        applyMigration();
        jdbc.update("UPDATE planner_comment_content SET content = 'after', edited_at = '2026-01-02 03:04:05', "
                + "author_notifications_enabled = FALSE WHERE comment_id = ?", id);
        jdbc.update("UPDATE planner_comment_stats SET upvote_count = 5 WHERE comment_id = ?", id);
        List<Map<String, Object>> written = newShape();

        applyMigration();

        assertThat(newShape()).isEqualTo(written).hasSize(1);
    }

    @Test
    void migration_WhenTheTablesExist_LeavesOnlyThePrimaryKeyIndex() {
        assertThat(jdbc.queryForList("SELECT CONCAT(table_name, '.', index_name) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() "
                + "AND table_name IN ('planner_comment_content', 'planner_comment_stats')", String.class))
                .containsExactlyInAnyOrder("planner_comment_content.PRIMARY", "planner_comment_stats.PRIMARY");
    }

    @Test
    void thread_WhenACommentHasNoContentRow_ShowsNeitherItNorItsReplies() throws Exception {
        Long unsplit = oldShapeRow("made by an old server", null, null, true, 0);
        Long reply = oldShapeRow("a reply", null, null, true, 0);
        jdbc.update("UPDATE planner_comments SET parent_comment_id = ?, depth = 1 WHERE id = ?", unsplit, reply);
        StoredComments.split(jdbc, reply);
        Long shown = oldShapeRow("shown", null, null, true, 0);
        StoredComments.split(jdbc, shown);

        mockMvc.perform(get("/api/planner/{id}/comments", planner.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").value("shown"));
    }

    @Test
    void commands_WhenACommentHasNoContentRow_AnswerNotFound() throws Exception {
        UUID unsplit = publicIdOf(oldShapeRow("made by an old server", null, null, true, 0));

        for (MockHttpServletRequestBuilder command : List.of(
                post("/api/comments/{id}/upvote", unsplit),
                put("/api/comments/{id}", unsplit).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"edited\"}"),
                delete("/api/comments/{id}", unsplit))) {
            AuthCookies.performAuthed(mockMvc, command.with(withCsrf()),
                            TestDataFactory.generateAccessToken(jwtTokenService, author))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
        }
    }

    private Long oldShapeRow(String content, String editedAt, String deletedAt, boolean notified, int upvotes) {
        String publicId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO planner_comments (public_id, planner_id, user_id, content, depth, upvote_count, "
                + "created_at, edited_at, deleted_at, author_notifications_enabled) "
                + "VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, 0, ?, NOW(), ?, ?, ?)",
                publicId, planner.id().toString(), author.getId(), content, upvotes, editedAt, deletedAt, notified);
        return jdbc.queryForObject("SELECT id FROM planner_comments WHERE public_id = UUID_TO_BIN(?)",
                Long.class, publicId);
    }

    private UUID publicIdOf(Long id) {
        return UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(public_id) FROM planner_comments WHERE id = ?",
                String.class, id));
    }

    private void dropNewTables() {
        jdbc.execute("DROP TABLE planner_comment_content");
        jdbc.execute("DROP TABLE planner_comment_stats");
    }

    private void applyMigration() {
        Arrays.stream(migration().split(";"))
                .filter(statement -> !statement.isBlank())
                .forEach(jdbc::execute);
    }

    private static String migration() {
        try {
            return new ClassPathResource("db/migration/V068__decompose_planner_comments.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<Map<String, Object>> oldShape() {
        return jdbc.queryForList("SELECT id AS comment_id, content, edited_at, deleted_at, "
                + "author_notifications_enabled, upvote_count FROM planner_comments ORDER BY id");
    }

    private List<Map<String, Object>> newShape() {
        return jdbc.queryForList("SELECT t.comment_id, t.content, t.edited_at, t.deleted_at, "
                + "t.author_notifications_enabled, s.upvote_count FROM planner_comment_content t "
                + "JOIN planner_comment_stats s ON s.comment_id = t.comment_id ORDER BY t.comment_id");
    }
}

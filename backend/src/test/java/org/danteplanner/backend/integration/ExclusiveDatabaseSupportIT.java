package org.danteplanner.backend.integration;

import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

class ExclusiveDatabaseSupportIT extends ExclusiveDatabaseSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private DataSource dataSource;

    @Test
    void emptyPlannerTables_WhenACommentReportIsLeft_EmptiesTheCommentsAndTheirReports() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        User author = TestDataFactory.createTestUser(userRepository, "report-author@example.com");
        User reporter = TestDataFactory.createTestUser(userRepository, "report-reporter@example.com");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, author, true);
        UUID commentPublicId = UUID.randomUUID();
        jdbc.update("INSERT INTO planner_comments (public_id, planner_id, user_id, content, depth, created_at) "
                + "VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, 'a reported comment', 0, NOW())",
                commentPublicId.toString(), planner.getId().toString(), author.getId());
        Long commentId = jdbc.queryForObject(
                "SELECT id FROM planner_comments WHERE public_id = UUID_TO_BIN(?)",
                Long.class, commentPublicId.toString());
        jdbc.update("INSERT INTO planner_comment_reports (comment_id, reporter_id, reason) VALUES (?, ?, 'SPAM')",
                commentId, reporter.getId());

        emptyPlannerTables(jdbc);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM planner_comment_reports", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM planner_comments", Integer.class)).isZero();
    }
}

package org.danteplanner.backend.integration;

import java.util.List;

import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.ResourceLock;

import org.danteplanner.backend.config.TestConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
@ResourceLock(ExclusiveDatabaseSupport.EXCLUSIVE_DATABASE)
public abstract class ExclusiveDatabaseSupport {

    static final String EXCLUSIVE_DATABASE = "exclusive-db";

    private static final List<String> PLANNER_TABLES = List.of(
            "planner_comment_reports", "planner_reports", "planner_subscriptions", "planner_bookmarks",
            "planner_comment_votes", "planner_comments", "planner_views", "planner_votes",
            "planner_entity_filter", "planner_keyword_filter", "planner_catalog",
            "planner_stats", "planner_moderation", "planner_publication",
            "planner_content", "planner");

    @DynamicPropertySource
    static void exclusiveDatabase(DynamicPropertyRegistry registry) {
        SharedMySqlContainerSupport.registerOwnDatabase(registry, "exclusive");
        SharedMySqlContainerSupport.registerGoogleStub(registry);
    }

    protected static void emptyPlannerTables(JdbcTemplate jdbc) {
        PLANNER_TABLES.forEach(table -> jdbc.update("DELETE FROM " + table));
    }
}

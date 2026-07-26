package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema drill for the planner god-table decomposition: the aggregate + projection
 * tables exist in the Flyway-migrated schema, the owner-mutated {@code planner_content}
 * row is bare (no secondary index, no inbound FK), and the legacy {@code planners} /
 * {@code planner_content_index} tables are gone.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerSchemaDecompositionIT extends SharedMySqlContainerSupport {


    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    private List<String> tables() {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);
    }

    private List<String> columns(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                String.class, table);
    }

    private List<String> indexes(String table) {
        return jdbc.queryForList(
                "SELECT DISTINCT index_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                String.class, table);
    }

    @Test
    @DisplayName("inv6_PlannerContent_NoSecondaryIndexNoInboundFk")
    void inv6_PlannerContent_NoSecondaryIndexNoInboundFk() {
        assertThat(indexes("planner_content"))
                .as("planner_content carries only its PRIMARY KEY (INV6: no secondary index)")
                .containsExactly("PRIMARY");

        Integer inboundFks = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.key_column_usage "
                        + "WHERE table_schema = DATABASE() AND referenced_table_name = 'planner_content'",
                Integer.class);
        assertThat(inboundFks)
                .as("no table FK-references planner_content (INV6: no inbound FK)")
                .isZero();
    }

    @Test
    @DisplayName("schemaDrill_AggregateAndProjectionTables_MatchTargetDdl")
    void schemaDrill_AggregateAndProjectionTables_MatchTargetDdl() {
        assertThat(tables()).contains(
                "planner", "planner_content", "planner_publication", "planner_moderation",
                "planner_stats", "planner_catalog", "planner_entity_filter", "planner_keyword_filter");

        assertThat(columns("planner_stats"))
                .contains("comment_count", "recommended_notified_at");

        assertThat(columns("planner_catalog"))
                .contains("planner_type", "category", "title", "selected_keywords",
                        "first_published_at", "recommended");
        assertThat(indexes("planner_catalog"))
                .contains("idx_catalog_recent", "idx_catalog_recommended", "ftx_title");

        assertThat(columns("planner_content"))
                .contains("title", "status", "category", "selected_keywords", "content",
                        "content_schema_version", "game_content_version", "sync_version",
                        "row_lock_version", "device_id", "last_modified_at", "deleted_at")
                .doesNotContain("saved_at");
    }

    @Test
    @DisplayName("schemaDrill_LegacyPlannersTables_Dropped")
    void schemaDrill_LegacyPlannersTables_Dropped() {
        assertThat(tables())
                .as("the planners god-table and planner_content_index are dropped by the cutover")
                .doesNotContain("planners", "planner_content_index");
    }
}

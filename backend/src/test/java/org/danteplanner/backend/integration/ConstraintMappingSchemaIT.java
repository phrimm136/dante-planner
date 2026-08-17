package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.shared.exception.KnownConstraint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ties {@link KnownConstraint}'s table names to the migrated schema.
 *
 * <p>Those names are strings matched against what MySQL reports for error 1062, and
 * {@code GlobalExceptionHandlerConstraintMappingTest} pins the response each produces without ever
 * connecting to a database. So a migration renaming a table leaves both green while, in production,
 * the violation stops classifying and a duplicate action surfaces as an unexpected conflict.</p>
 *
 * <p>Key names are deliberately not pinned: every row but one covers any unique key on its table,
 * which is what lets an index be renamed freely. What that wildcard rests on instead is the claim
 * that a unique key on these tables means "this user already did this to this target", so the test
 * asserts such a key exists rather than asserting its name.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class ConstraintMappingSchemaIT extends SharedMySqlContainerSupport {

    /**
     * The column naming the actor, per table. A unique key covering one of these plus a target is
     * the key whose violation means the action was already performed.
     */
    private static final Map<String, String> ACTOR_COLUMN = Map.of(
            "planner_votes", "user_id",
            "planner_bookmarks", "user_id",
            "planner_reports", "user_id",
            "planner_comment_reports", "reporter_id");

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    @TestFactory
    Stream<DynamicTest> knownConstraint_WhenSchemaIsRead_NamesATableThatExists() {
        return Stream.of(KnownConstraint.values()).map(constraint -> DynamicTest.dynamicTest(
                constraint.name() + " -> " + constraint.table(),
                () -> assertThat(tableNames())
                        .as("%s classifies violations by table name; a name that no longer matches "
                                + "the schema stops matching silently and its outcome falls to "
                                + "UNEXPECTED_CONFLICT", constraint.name())
                        .contains(constraint.table())));
    }

    @TestFactory
    Stream<DynamicTest> actionTable_WhenSchemaIsRead_CarriesAUniqueKeyNamingTheActor() {
        return ACTOR_COLUMN.entrySet().stream().map(entry -> DynamicTest.dynamicTest(
                entry.getKey() + " unique on " + entry.getValue(),
                () -> assertThat(uniqueKeyColumns(entry.getKey()))
                        .as("the wildcard row for %s reports every unique-key violation as a repeated "
                                + "action, which holds only while a unique key is keyed by %s",
                                entry.getKey(), entry.getValue())
                        .anyMatch(columns -> columns.contains(entry.getValue()))));
    }

    private List<String> tableNames() {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);
    }

    /** The column sets of every unique key on a table, PRIMARY included. */
    private List<List<String>> uniqueKeyColumns(String table) {
        List<String> indexes = jdbc.queryForList(
                "SELECT DISTINCT index_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND non_unique = 0",
                String.class, table);
        return indexes.stream()
                .map(index -> jdbc.queryForList(
                        "SELECT column_name FROM information_schema.statistics "
                                + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? "
                                + "ORDER BY seq_in_index",
                        String.class, table, index))
                .toList();
    }
}

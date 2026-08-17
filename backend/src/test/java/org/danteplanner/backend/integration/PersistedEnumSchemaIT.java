package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.shared.entity.ValuedEnum;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ties the enums MySQL stores as {@code ENUM} columns to the values those columns accept.
 *
 * <p>Nothing connects the two but a string. A constant renamed without its wire value stops
 * matching the column, and the failure is a write rejected at the driver or a read that cannot map
 * an existing row — neither of which a unit test of the enum can produce.</p>
 *
 * <p>{@code PlannerStatus} is the case that motivates this: its values are lower-case to match the
 * column, so the usual instinct to align a constant with its name silently breaks persistence.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PersistedEnumSchemaIT extends SharedMySqlContainerSupport {

    /** Each {@code ENUM} column, and the type whose values it stores. */
    private static final Map<String, Class<? extends Enum<?>>> PERSISTED = persistedColumns();

    private static Map<String, Class<? extends Enum<?>>> persistedColumns() {
        Map<String, Class<? extends Enum<?>>> columns = new LinkedHashMap<>();
        // Only columns the schema still declares as ENUM: the decomposition moved
        // planner_content.status and .category to VARCHAR, where the database enforces nothing.
        columns.put("planner.planner_type", PlannerType.class);
        columns.put("planner_votes.vote_type", VoteType.class);
        columns.put("planner_entity_filter.entity_type", ContentEntityType.class);
        return columns;
    }

    /**
     * Members a column still accepts that the code deliberately stopped producing. Removing a
     * constant does not narrow the column, so the capacity outlives the feature: V023 deleted every
     * downvote row and left {@code ENUM('UP','DOWN')} in place. Listed rather than ignored so the
     * drift stays visible and a new one still fails.
     */
    private static final Map<String, Set<String>> RETIRED_MEMBERS = Map.of(
            "planner_votes.vote_type", Set.of("DOWN"));

    private static final Pattern ENUM_MEMBERS = Pattern.compile("^enum\\((.*)\\)$");

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    @TestFactory
    Stream<DynamicTest> persistedEnum_WhenSchemaIsRead_AcceptsEveryDeclaredValue() {
        return PERSISTED.entrySet().stream().map(entry -> DynamicTest.dynamicTest(
                entry.getKey() + " ← " + entry.getValue().getSimpleName(),
                () -> assertThat(columnValues(entry.getKey()))
                        .as("%s stores %s by its wire value; a constant the column does not accept "
                                + "fails at the driver on write and cannot be mapped on read",
                                entry.getKey(), entry.getValue().getSimpleName())
                        .containsAll(wireValues(entry.getValue()))));
    }

    @TestFactory
    Stream<DynamicTest> persistedColumn_WhenSchemaIsRead_AcceptsNothingTheEnumCannotProduce() {
        return PERSISTED.entrySet().stream().map(entry -> DynamicTest.dynamicTest(
                entry.getKey() + " has no orphan member",
                () -> {
                    Set<String> retired =
                            RETIRED_MEMBERS.getOrDefault(entry.getKey(), Set.of());
                    Set<String> orphans = columnValues(entry.getKey()).stream()
                            .filter(value -> !wireValues(entry.getValue()).contains(value))
                            .filter(value -> !retired.contains(value))
                            .collect(Collectors.toSet());
                    // A member the enum cannot produce is a row shape nothing reads: either a
                    // constant was dropped without a migration, or one was never added.
                    assertThat(orphans)
                            .as("%s accepts values %s has no constant for",
                                    entry.getKey(), entry.getValue().getSimpleName())
                            .isEmpty();
                }));
    }

    /**
     * What a constant is stored as: its declared value where the type has one, otherwise the
     * constant name, which is what Hibernate writes for a plain {@code @Enumerated(STRING)}.
     */
    private static Set<String> wireValues(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(constant -> constant instanceof ValuedEnum valued
                        ? valued.getValue()
                        : constant.name())
                .collect(Collectors.toSet());
    }

    /** The members of an {@code ENUM} column, read from its declared type. */
    private Set<String> columnValues(String qualifiedColumn) {
        String[] parts = qualifiedColumn.split("\\.", 2);
        List<String> declared = jdbc.queryForList(
                "SELECT column_type FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                String.class, parts[0], parts[1]);
        assertThat(declared)
                .as("%s is not a column in the migrated schema", qualifiedColumn)
                .hasSize(1);

        Matcher matcher = ENUM_MEMBERS.matcher(declared.get(0));
        assertThat(matcher.matches())
                .as("%s is declared %s, not an ENUM", qualifiedColumn, declared.get(0))
                .isTrue();

        return Arrays.stream(matcher.group(1).split(","))
                .map(member -> member.trim().replaceAll("^'|'$", ""))
                .collect(Collectors.toSet());
    }
}

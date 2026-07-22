package org.danteplanner.backend.integration;

import io.micrometer.core.instrument.MeterRegistry;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerDriftReconciler;
import org.danteplanner.backend.planner.service.PlannerDriftReconciler.DriftRecord;
import org.danteplanner.backend.planner.service.PlannerFilterService;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reconciler drill: seeded drift in every audited dimension is detected and
 * reported as structured records — and deliberately NOT repaired.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerReconcilerIT extends SharedMySqlContainerSupport {

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registerSharedMysql(registry, "planner_reconciler_it");
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerCatalogRepository catalogRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerEntityFilterRepository entityFilterRepository;

    @Autowired
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Autowired
    private PlannerCatalogService catalogService;

    @Autowired
    private PlannerFilterService filterService;

    @Autowired
    private PlannerDriftReconciler reconciler;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private User owner;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        cleanUp();
        owner = TestDataFactory.createTestUser(userRepository, "reconciler-owner@example.com");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        for (String table : List.of(
                "planner_comments", "planner_votes",
                "planner_entity_filter", "planner_keyword_filter", "planner_catalog",
                "planner_stats", "planner_moderation", "planner_publication",
                "planner_content", "planner")) {
            jdbc.update("DELETE FROM " + table);
        }
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);
    }

    private Planner publishClean(String title) {
        Planner planner = TestDataFactory.planner(owner)
                .title(title)
                .selectedKeywords(Set.of("Sinking"))
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
        filterService.rebuildFilters(planner.getId(), planner.getContentJson(), planner.getSelectedKeywords());
        return planner;
    }

    private Set<String> kindsFor(List<DriftRecord> records, UUID plannerId) {
        return records.stream()
                .filter(r -> r.plannerId().equals(plannerId))
                .map(DriftRecord::kind)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("reconciler-detects-drift: each seeded divergence yields a structured record and is not auto-repaired")
    void reconcilerDetectsDrift_WhenDriftSeeded_ReportsWithoutRepair() {
        // A clean planner: must produce no records
        Planner clean = publishClean("Clean");

        // Upvote drift: counter says 5, one real vote row
        Planner voteDrift = publishClean("Vote Drift");
        jdbc.update("INSERT INTO planner_votes (user_id, planner_id, vote_type, created_at, version) "
                + "VALUES (?, UUID_TO_BIN(?), 'UP', NOW(), 0)", owner.getId(), voteDrift.getId().toString());
        jdbc.update("UPDATE planner_stats SET upvotes = 5 WHERE planner_id = UUID_TO_BIN(?)",
                voteDrift.getId().toString());

        // Comment-count drift: counter says 3, one live comment row
        Planner commentDrift = publishClean("Comment Drift");
        jdbc.update("INSERT INTO planner_comments (public_id, planner_id, user_id, content, depth, created_at) "
                + "VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, 'seeded', 0, NOW())",
                UUID.randomUUID().toString(), commentDrift.getId().toString(), owner.getId());
        jdbc.update("UPDATE planner_stats SET comment_count = 3 WHERE planner_id = UUID_TO_BIN(?)",
                commentDrift.getId().toString());

        // Catalog membership drift, both directions: a visible planner missing its
        // row, and a row surviving for an unpublished planner
        Planner missingRow = publishClean("Missing Catalog Row");
        jdbc.update("DELETE FROM planner_catalog WHERE planner_id = UUID_TO_BIN(?)",
                missingRow.getId().toString());
        Planner orphanRow = publishClean("Orphan Catalog Row");
        jdbc.update("UPDATE planner_publication SET published = FALSE WHERE planner_id = UUID_TO_BIN(?)",
                orphanRow.getId().toString());

        // Filter drift: an entity row deleted, a keyword row that should not exist
        Planner filterDrift = publishClean("Filter Drift");
        jdbc.update("DELETE FROM planner_entity_filter WHERE planner_id = UUID_TO_BIN(?) LIMIT 1",
                filterDrift.getId().toString());
        jdbc.update("INSERT INTO planner_keyword_filter (keyword, planner_id) "
                + "VALUES ('DawnTeam', UUID_TO_BIN(?))", filterDrift.getId().toString());

        // Recommended drift: flagged recommended while far below the threshold
        Planner recommendedDrift = publishClean("Recommended Drift");
        jdbc.update("UPDATE planner_catalog SET recommended = TRUE WHERE planner_id = UUID_TO_BIN(?)",
                recommendedDrift.getId().toString());

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(kindsFor(records, clean.getId())).as("clean planner yields no drift").isEmpty();
        assertThat(kindsFor(records, voteDrift.getId())).contains("upvotes");
        assertThat(kindsFor(records, commentDrift.getId())).contains("comment_count");
        assertThat(kindsFor(records, missingRow.getId())).contains("catalog_membership");
        assertThat(kindsFor(records, orphanRow.getId())).contains("catalog_membership");
        assertThat(kindsFor(records, filterDrift.getId()))
                .contains("entity_filter", "keyword_filter");
        assertThat(kindsFor(records, recommendedDrift.getId())).contains("recommended");

        // Metric: one counter increment per record
        double counted = meterRegistry.find("planner_reconciler_drift_total").counters().stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count)
                .sum();
        assertThat(counted).as("each drift record increments the drift metric")
                .isGreaterThanOrEqualTo(records.size());

        // No auto-repair: every seeded divergence is still in place
        assertThat(jdbc.queryForObject("SELECT upvotes FROM planner_stats WHERE planner_id = UUID_TO_BIN(?)",
                Integer.class, voteDrift.getId().toString())).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT comment_count FROM planner_stats WHERE planner_id = UUID_TO_BIN(?)",
                Integer.class, commentDrift.getId().toString())).isEqualTo(3);
        assertThat(catalogRepository.existsById(missingRow.getId())).isFalse();
        assertThat(catalogRepository.existsById(orphanRow.getId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM planner_keyword_filter "
                        + "WHERE planner_id = UUID_TO_BIN(?) AND keyword = 'DawnTeam'",
                Integer.class, filterDrift.getId().toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT recommended FROM planner_catalog WHERE planner_id = UUID_TO_BIN(?)",
                Boolean.class, recommendedDrift.getId().toString())).isTrue();
    }
}

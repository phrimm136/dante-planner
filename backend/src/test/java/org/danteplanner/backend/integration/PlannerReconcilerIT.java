package org.danteplanner.backend.integration;

import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.core.LockProvider;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
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
@Import({TestConfig.class, PlannerReconcilerIT.ReconcilerLockHarness.class})
class PlannerReconcilerIT extends SharedMySqlContainerSupport {

    /**
     * Stands in for the fleet lock the pass takes.
     *
     * <p>The lock is always granted because this class drives {@code reconcile()} directly and
     * several times over: the real provider holds the lock for {@code lockAtLeastFor} past each
     * release, and a refused call returns null rather than a record list. Fleet arbitration is
     * {@code ShedLockMultiPodIT}'s subject, not this one's.</p>
     */
    @TestConfiguration
    static class ReconcilerLockHarness {

        @Bean
        @Primary
        LockProvider lockProvider() {
            return configuration -> Optional.of(() -> { });
        }
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
        owner = TestDataFactory.createTestUser(userRepository, "reconciler-owner@example.com");
    }

    private Planner publishClean(String title) {
        return publishClean(title, Set.of("Sinking"));
    }

    private Planner publishClean(String title, Set<String> keywords) {
        Planner planner = TestDataFactory.planner(owner)
                .title(title)
                .selectedKeywords(keywords)
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
        filterService.rebuildFilters(planner.getId());
        return planner;
    }

    private Integer entityFilterRows(UUID plannerId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM planner_entity_filter "
                + "WHERE planner_id = UUID_TO_BIN(?)", Integer.class, plannerId.toString());
    }

    private Set<String> kindsFor(List<DriftRecord> records, UUID plannerId) {
        return records.stream()
                .filter(r -> r.plannerId().equals(plannerId))
                .map(DriftRecord::kind)
                .collect(Collectors.toSet());
    }

    private List<DriftRecord> recordsFor(List<DriftRecord> records, UUID plannerId, String kind) {
        return records.stream()
                .filter(r -> r.plannerId().equals(plannerId) && r.kind().equals(kind))
                .toList();
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

    @Test
    @DisplayName("moderation-less planner is audited, not skipped: its recommended drift is still reported")
    void recommendedDrift_WhenModerationRowMissing_IsStillReported() {
        Planner noModeration = publishClean("No Moderation Row");
        jdbc.update("DELETE FROM planner_moderation WHERE planner_id = UUID_TO_BIN(?)",
                noModeration.getId().toString());
        jdbc.update("UPDATE planner_catalog SET recommended = TRUE WHERE planner_id = UUID_TO_BIN(?)",
                noModeration.getId().toString());

        Set<String> kinds = kindsFor(reconciler.reconcile(), noModeration.getId());

        assertThat(kinds)
                .as("an absent moderation row hides nothing, so the flag derives FALSE and disagrees")
                .contains("recommended");
        assertThat(kinds)
                .as("every other audit reads the planner as visible rather than dropping it")
                .doesNotContain("catalog_membership", "entity_filter", "keyword_filter");
    }

    @Test
    @DisplayName("reconciler-skips-unreadable: a planner whose stored keywords cannot be rebuilt is left out of the index audits, and its catalog copy is compared as the empty set the runtime serves")
    void reconcilerSkipsUnreadable_WhenContentCannotBeParsed_ReportsNoFilterDrift() {
        Planner unreadable = publishClean("Unreadable Content");
        assertThat(entityFilterRows(unreadable.getId()))
                .as("the planner starts with a correctly built index").isPositive();

        // The column enforces well-formed JSON, so a document is unreadable by carrying a shape the
        // rebuild cannot consume rather than by being malformed.
        jdbc.update("UPDATE planner_content SET selected_keywords = '{\"not\": \"a list\"}' "
                + "WHERE planner_id = UUID_TO_BIN(?)", unreadable.getId().toString());

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(kindsFor(records, unreadable.getId()))
                .as("an unrebuildable document is unknown, not empty: its indexed rows are not drift")
                .doesNotContain("entity_filter", "keyword_filter");
        assertThat(recordsFor(records, unreadable.getId(), "catalog_keywords"))
                .as("the runtime serves the corrupt column as no keywords, so the catalog's copy "
                        + "is a divergence a reader can see")
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.expected()).isEqualTo("[]");
                    assertThat(record.actual()).isEqualTo("[Sinking]");
                });
        assertThat(entityFilterRows(unreadable.getId()))
                .as("nothing is repaired away either").isPositive();
    }

    @Test
    @DisplayName("catalog scalar drift: a stale title copy is reported once, naming both sides")
    void catalogTitleDrift_WhenTheCatalogCopyDivergesFromTheContentRow_ReportsOneRecord() {
        Planner titleDrift = publishClean("Catalog Title Drift");
        jdbc.update("UPDATE planner_catalog SET title = 'Stale Copy' WHERE planner_id = UUID_TO_BIN(?)",
                titleDrift.getId().toString());

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(recordsFor(records, titleDrift.getId(), "catalog_title"))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.expected()).isEqualTo("Catalog Title Drift");
                    assertThat(record.actual()).isEqualTo("Stale Copy");
                });
        assertThat(kindsFor(records, titleDrift.getId()))
                .as("only the diverged column is reported")
                .doesNotContain("catalog_category");
        assertThat(jdbc.queryForObject("SELECT title FROM planner_catalog WHERE planner_id = UUID_TO_BIN(?)",
                String.class, titleDrift.getId().toString()))
                .as("the audit repairs nothing").isEqualTo("Stale Copy");
    }

    @Test
    @DisplayName("catalog scalar drift: a copy differing only by case or by a trailing space is still stale")
    void catalogTitleDrift_WhenTheCopyDiffersOnlyByCaseOrTrailingSpace_ReportsEachAsDrift() {
        Planner caseOnly = publishClean("Sinking Build");
        jdbc.update("UPDATE planner_catalog SET title = 'sinking build' WHERE planner_id = UUID_TO_BIN(?)",
                caseOnly.getId().toString());

        Planner spaceOnly = publishClean("Trailing Space");
        jdbc.update("UPDATE planner_catalog SET title = 'Trailing Space ' WHERE planner_id = UUID_TO_BIN(?)",
                spaceOnly.getId().toString());

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(recordsFor(records, caseOnly.getId(), "catalog_title"))
                .as("the columns collate case-insensitively, which is where a half-landed rename hides")
                .singleElement()
                .satisfies(record -> assertThat(record.actual()).isEqualTo("sinking build"));
        assertThat(recordsFor(records, spaceOnly.getId(), "catalog_title"))
                .as("the columns collate PAD SPACE, so a trailing space is invisible to the default comparison")
                .singleElement()
                .satisfies(record -> assertThat(record.actual()).isEqualTo("Trailing Space "));
    }

    @Test
    @DisplayName("catalog keyword drift: element order is not drift, a different set is")
    void catalogKeywordDrift_WhenTheStoredArraysDifferInOrderAndInMembership_ReportsOnlyTheSetDifference() {
        Planner reordered = publishClean("Reordered Keywords", Set.of("Burst", "Sinking"));
        jdbc.update("UPDATE planner_catalog SET selected_keywords = '[\"Sinking\", \"Burst\"]' "
                + "WHERE planner_id = UUID_TO_BIN(?)", reordered.getId().toString());

        Planner different = publishClean("Different Keywords", Set.of("Burst", "Sinking"));
        jdbc.update("UPDATE planner_catalog SET selected_keywords = '[\"Combustion\"]' "
                + "WHERE planner_id = UUID_TO_BIN(?)", different.getId().toString());

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(kindsFor(records, reordered.getId()))
                .as("the stored array is order-bearing and the set it denotes is not")
                .doesNotContain("catalog_keywords");
        assertThat(recordsFor(records, different.getId(), "catalog_keywords"))
                .as("a differing membership is drift")
                .hasSize(1);
    }

    @Test
    @DisplayName("catalog copies of a tombstoned planner: one leftover row is one finding, not three")
    void catalogCopyDrift_WhenThePlannerIsTombstoned_ReportsMembershipAlone() {
        Planner tombstoned = publishClean("Tombstoned Copy");
        jdbc.update("UPDATE planner_catalog SET title = 'Stale', selected_keywords = '[\"Combustion\"]' "
                + "WHERE planner_id = UUID_TO_BIN(?)", tombstoned.getId().toString());
        jdbc.update("UPDATE planner_content SET deleted_at = NOW(6) WHERE planner_id = UUID_TO_BIN(?)",
                tombstoned.getId().toString());

        Set<String> kinds = kindsFor(reconciler.reconcile(), tombstoned.getId());

        assertThat(kinds)
                .as("the leftover row is the bug, and the audit that owns it already names it")
                .contains("catalog_membership");
        assertThat(kinds)
                .as("its copies are stale because the row should be gone, which is not a second bug")
                .doesNotContain("catalog_title", "catalog_keywords");
    }

    @Test
    @DisplayName("recommendation stamp with neither an event nor a notification row is reported; either row clears it")
    void recommendedNotificationDrift_WhenTheStampCarriesNeitherRow_ReportsUntilEitherExists() {
        Planner neither = publishClean("Stamped Without Effect");
        Planner withEvent = publishClean("Stamped With Event");
        Planner withNotification = publishClean("Stamped With Notification");
        stampRecommended(neither);
        stampRecommended(withEvent);
        stampRecommended(withNotification);

        jdbc.update("INSERT INTO domain_events (event_type, aggregate_id, payload) "
                        + "VALUES ('PLANNER_RECOMMENDED', UUID_TO_BIN(?), ?)",
                withEvent.getId().toString(), "{\"ownerId\": " + owner.getId() + "}");
        jdbc.update("INSERT INTO notifications (user_id, content_id, notification_type, public_id) "
                        + "VALUES (?, ?, 'PLANNER_RECOMMENDED', UUID_TO_BIN(?))",
                owner.getId(), withNotification.getId().toString(), UUID.randomUUID().toString());

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(recordsFor(records, neither.getId(), "recommended_notification"))
                .as("the latch was taken, so the owner was owed a notification nothing carries")
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.expected()).isEqualTo("event or notification row");
                    assertThat(record.actual()).isEqualTo("neither");
                });
        assertThat(kindsFor(records, withEvent.getId()))
                .as("an event row aged out of retention after delivery is not drift, so its presence clears")
                .doesNotContain("recommended_notification");
        assertThat(kindsFor(records, withNotification.getId()))
                .as("a recipient who deleted the row still received it, so its presence clears")
                .doesNotContain("recommended_notification");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE content_id = ?",
                Integer.class, neither.getId().toString()))
                .as("the audit repairs nothing").isZero();
    }

    @Test
    @DisplayName("recommendation stamp older than the audit window: absence is age, not drift")
    void recommendedNotificationDrift_WhenTheStampPredatesTheAuditWindow_ReportsNothing() {
        Planner aged = publishClean("Stamped Long Ago");
        jdbc.update("UPDATE planner_stats SET recommended_notified_at = DATE_SUB(NOW(6), INTERVAL 60 DAY) "
                + "WHERE planner_id = UUID_TO_BIN(?)", aged.getId().toString());

        assertThat(kindsFor(reconciler.reconcile(), aged.getId()))
                .as("both carriers are swept on their own retention, so an old stamp with neither "
                        + "row is the expected end state rather than a finding")
                .doesNotContain("recommended_notification");
    }

    private void stampRecommended(Planner planner) {
        jdbc.update("UPDATE planner_stats SET recommended_notified_at = NOW(6) "
                + "WHERE planner_id = UUID_TO_BIN(?)", planner.getId().toString());
    }
}

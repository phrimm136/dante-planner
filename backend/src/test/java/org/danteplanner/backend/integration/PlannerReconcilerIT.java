package org.danteplanner.backend.integration;

import org.danteplanner.backend.planner.precondition.PlannerPublishValidator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.MeterRegistry;
import org.danteplanner.backend.support.PlannerSeeds;
import org.danteplanner.backend.support.StoredComments;
import org.danteplanner.backend.planner.entity.Publication;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerDriftReconciler;
import org.danteplanner.backend.planner.service.PlannerDriftReconciler.DriftRecord;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.FilterRowsSync;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.UpsertRequests;
import org.danteplanner.backend.planner.sync.SyncVersionValidator.WriteMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reconciler drill: seeded drift in every audited dimension is detected and
 * reported as structured records — and deliberately NOT repaired.
 */
class PlannerReconcilerIT extends ExclusiveDatabaseSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerSeeds plannerSeeds;

    @Autowired
    private PlannerCatalogRepository catalogRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerCatalogService catalogService;

    @Autowired
    private PlannerCommandService commandService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PlannerDriftReconciler reconciler;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    private static final String SIX_FLOOR_CONTENT = TestDataFactory.VALID_CONTENT.replace(
            "{\"themePackId\":\"1005\",\"difficulty\":0,\"giftIds\":[]}",
            "{\"themePackId\":\"1005\",\"difficulty\":0,\"giftIds\":[]},"
                    + "{\"themePackId\":\"1006\",\"difficulty\":0,\"giftIds\":[\"9004\"]}");
    private User owner;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        owner = TestDataFactory.createTestUser(userRepository, "reconciler-owner@example.com");
    }

    private StoredPlanner publishClean(String title) {
        return publishClean(title, Set.of("Sinking"));
    }

    private StoredPlanner publishClean(String title, Set<String> keywords) {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .title(title)
                .selectedKeywords(keywords)
                .published(true)
                .save(plannerSeeds);
        statsRepository.save(PlannerStats.builder().plannerId(planner.id()).build());
        FilterRowsSync.readCommitted(transactionManager)
                .executeWithoutResult(status -> catalogService.onBecameVisible(planner,
                        new PlannerPublishValidator().requirePublishable(planner), (Publication.Published) planner.publication()));
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

    // reconciler-detects-drift
    @Test
    void reconcilerDetectsDrift_WhenDriftSeeded_ReportsWithoutRepair() {
        // A clean planner: must produce no records
        StoredPlanner clean = publishClean("Clean");

        // Upvote drift: counter says 5, one real vote row
        StoredPlanner voteDrift = publishClean("Vote Drift");
        jdbc.update("INSERT INTO planner_votes (user_id, planner_id, vote_type, created_at, version) "
                + "VALUES (?, UUID_TO_BIN(?), 'UP', NOW(), 0)", owner.getId(), voteDrift.id().toString());
        jdbc.update("UPDATE planner_stats SET upvotes = 5 WHERE planner_id = UUID_TO_BIN(?)",
                voteDrift.id().toString());

        // Comment-count drift: counter says 3, one live comment row
        StoredPlanner commentDrift = publishClean("Comment Drift");
        jdbc.update("INSERT INTO planner_comments (public_id, planner_id, user_id, content, depth, created_at) "
                + "VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, 'seeded', 0, NOW())",
                UUID.randomUUID().toString(), commentDrift.id().toString(), owner.getId());
        StoredComments.split(jdbc, jdbc.queryForObject(
                "SELECT id FROM planner_comments WHERE planner_id = UUID_TO_BIN(?)",
                Long.class, commentDrift.id().toString()));
        jdbc.update("UPDATE planner_stats SET comment_count = 3 WHERE planner_id = UUID_TO_BIN(?)",
                commentDrift.id().toString());

        // Catalog membership drift, both directions: a visible planner missing its
        // row, and a row surviving for an unpublished planner
        StoredPlanner missingRow = publishClean("Missing Catalog Row");
        jdbc.update("DELETE FROM planner_catalog WHERE planner_id = UUID_TO_BIN(?)",
                missingRow.id().toString());
        StoredPlanner orphanRow = publishClean("Orphan Catalog Row");
        jdbc.update("UPDATE planner_publication SET published = FALSE WHERE planner_id = UUID_TO_BIN(?)",
                orphanRow.id().toString());

        // Filter drift: an entity row deleted, a keyword row that should not exist
        StoredPlanner filterDrift = publishClean("Filter Drift");
        jdbc.update("DELETE FROM planner_entity_filter WHERE planner_id = UUID_TO_BIN(?) LIMIT 1",
                filterDrift.id().toString());
        jdbc.update("INSERT INTO planner_keyword_filter (keyword, planner_id) "
                + "VALUES ('DawnTeam', UUID_TO_BIN(?))", filterDrift.id().toString());

        // Recommended drift: flagged recommended while far below the threshold
        StoredPlanner recommendedDrift = publishClean("Recommended Drift");
        jdbc.update("UPDATE planner_catalog SET recommended = TRUE WHERE planner_id = UUID_TO_BIN(?)",
                recommendedDrift.id().toString());

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(kindsFor(records, clean.id())).as("clean planner yields no drift").isEmpty();
        assertThat(kindsFor(records, voteDrift.id())).contains("upvotes");
        assertThat(kindsFor(records, commentDrift.id())).contains("comment_count");
        assertThat(kindsFor(records, missingRow.id())).contains("catalog_membership");
        assertThat(kindsFor(records, orphanRow.id())).contains("catalog_membership");
        assertThat(kindsFor(records, filterDrift.id()))
                .contains("entity_filter", "keyword_filter");
        assertThat(kindsFor(records, recommendedDrift.id())).contains("recommended");

        // Metric: one counter increment per record
        double counted = meterRegistry.find("planner_reconciler_drift_total").counters().stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count)
                .sum();
        assertThat(counted).as("each drift record increments the drift metric")
                .isGreaterThanOrEqualTo(records.size());

        // No auto-repair: every seeded divergence is still in place
        assertThat(jdbc.queryForObject("SELECT upvotes FROM planner_stats WHERE planner_id = UUID_TO_BIN(?)",
                Integer.class, voteDrift.id().toString())).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT comment_count FROM planner_stats WHERE planner_id = UUID_TO_BIN(?)",
                Integer.class, commentDrift.id().toString())).isEqualTo(3);
        assertThat(catalogRepository.existsById(missingRow.id())).isFalse();
        assertThat(catalogRepository.existsById(orphanRow.id())).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM planner_keyword_filter "
                        + "WHERE planner_id = UUID_TO_BIN(?) AND keyword = 'DawnTeam'",
                Integer.class, filterDrift.id().toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT recommended FROM planner_catalog WHERE planner_id = UUID_TO_BIN(?)",
                Boolean.class, recommendedDrift.id().toString())).isTrue();
    }

    @Test
    void recommendedDrift_WhenModerationRowMissing_IsStillReported() {
        StoredPlanner noModeration = publishClean("No Moderation Row");
        jdbc.update("DELETE FROM planner_moderation WHERE planner_id = UUID_TO_BIN(?)",
                noModeration.id().toString());
        jdbc.update("UPDATE planner_catalog SET recommended = TRUE WHERE planner_id = UUID_TO_BIN(?)",
                noModeration.id().toString());

        Set<String> kinds = kindsFor(reconciler.reconcile(), noModeration.id());

        assertThat(kinds)
                .as("an absent moderation row hides nothing, so the flag derives FALSE and disagrees")
                .contains("recommended");
        assertThat(kinds)
                .as("every other audit reads the planner as visible rather than dropping it")
                .doesNotContain("catalog_membership", "entity_filter", "keyword_filter");
    }

    @Test
    void corruptKeywordColumn_WhenTheContentIsReadable_ReportsCatalogDriftButNoFilterDrift() {
        StoredPlanner unreadable = publishClean("Unreadable Content");
        assertThat(entityFilterRows(unreadable.id()))
                .as("the planner starts with a correctly built index").isPositive();

        // The column enforces well-formed JSON, so a document is unreadable by carrying a shape the
        // rebuild cannot consume rather than by being malformed.
        jdbc.update("UPDATE planner_content SET selected_keywords = '{\"not\": \"a list\"}' "
                + "WHERE planner_id = UUID_TO_BIN(?)", unreadable.id().toString());

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(kindsFor(records, unreadable.id()))
                .as("an unrebuildable document is unknown, not empty: its indexed rows are not drift")
                .doesNotContain("entity_filter", "keyword_filter");
        assertThat(recordsFor(records, unreadable.id(), "catalog_keywords"))
                .as("the runtime serves the corrupt column as no keywords, so the catalog's copy "
                        + "is a divergence a reader can see")
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.expected()).isEqualTo("[]");
                    assertThat(record.actual()).isEqualTo("[Sinking]");
                });
        assertThat(entityFilterRows(unreadable.id()))
                .as("nothing is repaired away either").isPositive();
    }

    @Test
    void catalogTitleDrift_WhenTheCatalogCopyDivergesFromTheContentRow_ReportsOneRecord() {
        StoredPlanner titleDrift = publishClean("Catalog Title Drift");
        jdbc.update("UPDATE planner_catalog SET title = 'Stale Copy' WHERE planner_id = UUID_TO_BIN(?)",
                titleDrift.id().toString());

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(recordsFor(records, titleDrift.id(), "catalog_title"))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.expected()).isEqualTo("Catalog Title Drift");
                    assertThat(record.actual()).isEqualTo("Stale Copy");
                });
        assertThat(kindsFor(records, titleDrift.id()))
                .as("only the diverged column is reported")
                .doesNotContain("catalog_category");
        assertThat(jdbc.queryForObject("SELECT title FROM planner_catalog WHERE planner_id = UUID_TO_BIN(?)",
                String.class, titleDrift.id().toString()))
                .as("the audit repairs nothing").isEqualTo("Stale Copy");
    }

    @Test
    void catalogTitleDrift_WhenTheCopyDiffersOnlyByCaseOrTrailingSpace_ReportsEachAsDrift() {
        StoredPlanner caseOnly = publishClean("Sinking Build");
        jdbc.update("UPDATE planner_catalog SET title = 'sinking build' WHERE planner_id = UUID_TO_BIN(?)",
                caseOnly.id().toString());

        StoredPlanner spaceOnly = publishClean("Trailing Space");
        jdbc.update("UPDATE planner_catalog SET title = 'Trailing Space ' WHERE planner_id = UUID_TO_BIN(?)",
                spaceOnly.id().toString());

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(recordsFor(records, caseOnly.id(), "catalog_title"))
                .as("the columns collate case-insensitively, which is where a half-landed rename hides")
                .singleElement()
                .satisfies(record -> assertThat(record.actual()).isEqualTo("sinking build"));
        assertThat(recordsFor(records, spaceOnly.id(), "catalog_title"))
                .as("the columns collate PAD SPACE, so a trailing space is invisible to the default comparison")
                .singleElement()
                .satisfies(record -> assertThat(record.actual()).isEqualTo("Trailing Space "));
    }

    @Test
    void catalogKeywordDrift_WhenTheStoredArraysDifferInOrderAndInMembership_ReportsOnlyTheSetDifference() {
        StoredPlanner reordered = publishClean("Reordered Keywords", Set.of("Burst", "Sinking"));
        jdbc.update("UPDATE planner_catalog SET selected_keywords = '[\"Sinking\", \"Burst\"]' "
                + "WHERE planner_id = UUID_TO_BIN(?)", reordered.id().toString());

        StoredPlanner different = publishClean("Different Keywords", Set.of("Burst", "Sinking"));
        jdbc.update("UPDATE planner_catalog SET selected_keywords = '[\"Combustion\"]' "
                + "WHERE planner_id = UUID_TO_BIN(?)", different.id().toString());

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(kindsFor(records, reordered.id()))
                .as("the stored array is order-bearing and the set it denotes is not")
                .doesNotContain("catalog_keywords");
        assertThat(recordsFor(records, different.id(), "catalog_keywords"))
                .as("a differing membership is drift")
                .hasSize(1);
    }

    @Test
    void catalogCopyDrift_WhenThePlannerIsTombstoned_ReportsMembershipAlone() {
        StoredPlanner tombstoned = publishClean("Tombstoned Copy");
        jdbc.update("UPDATE planner_catalog SET title = 'Stale', selected_keywords = '[\"Combustion\"]' "
                + "WHERE planner_id = UUID_TO_BIN(?)", tombstoned.id().toString());
        jdbc.update("UPDATE planner_content SET deleted_at = NOW(6) WHERE planner_id = UUID_TO_BIN(?)",
                tombstoned.id().toString());

        Set<String> kinds = kindsFor(reconciler.reconcile(), tombstoned.id());

        assertThat(kinds)
                .as("the leftover row is the bug, and the audit that owns it already names it")
                .contains("catalog_membership");
        assertThat(kinds)
                .as("its copies are stale because the row should be gone, which is not a second bug")
                .doesNotContain("catalog_title", "catalog_keywords");
    }

    @Test
    void recommendedNotificationDrift_WhenTheStampCarriesNeitherRow_ReportsUntilEitherExists() {
        StoredPlanner neither = publishClean("Stamped Without Effect");
        StoredPlanner withEvent = publishClean("Stamped With Event");
        StoredPlanner withNotification = publishClean("Stamped With Notification");
        stampRecommended(neither);
        stampRecommended(withEvent);
        stampRecommended(withNotification);

        jdbc.update("INSERT INTO domain_events (event_type, aggregate_id, payload) "
                        + "VALUES ('PLANNER_RECOMMENDED', UUID_TO_BIN(?), ?)",
                withEvent.id().toString(), "{\"ownerId\": " + owner.getId() + "}");
        jdbc.update("INSERT INTO notifications (user_id, content_id, notification_type, public_id) "
                        + "VALUES (?, ?, 'PLANNER_RECOMMENDED', UUID_TO_BIN(?))",
                owner.getId(), withNotification.id().toString(), UUID.randomUUID().toString());

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(recordsFor(records, neither.id(), "recommended_notification"))
                .as("the latch was taken, so the owner was owed a notification nothing carries")
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.expected()).isEqualTo("event or notification row");
                    assertThat(record.actual()).isEqualTo("neither");
                });
        assertThat(kindsFor(records, withEvent.id()))
                .as("an event row aged out of retention after delivery is not drift, so its presence clears")
                .doesNotContain("recommended_notification");
        assertThat(kindsFor(records, withNotification.id()))
                .as("a recipient who deleted the row still received it, so its presence clears")
                .doesNotContain("recommended_notification");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE content_id = ?",
                Integer.class, neither.id().toString()))
                .as("the audit repairs nothing").isZero();
    }

    @Test
    void recommendedNotificationDrift_WhenTheStampPredatesTheAuditWindow_ReportsNothing() {
        StoredPlanner aged = publishClean("Stamped Long Ago");
        jdbc.update("UPDATE planner_stats SET recommended_notified_at = DATE_SUB(NOW(6), INTERVAL 60 DAY) "
                + "WHERE planner_id = UUID_TO_BIN(?)", aged.id().toString());

        assertThat(kindsFor(reconciler.reconcile(), aged.id()))
                .as("both carriers are swept on their own retention, so an old stamp with neither "
                        + "row is the expected end state rather than a finding")
                .doesNotContain("recommended_notification");
    }

    private void stampRecommended(StoredPlanner planner) {
        jdbc.update("UPDATE planner_stats SET recommended_notified_at = NOW(6) "
                + "WHERE planner_id = UUID_TO_BIN(?)", planner.id().toString());
    }

    @Test
    void reconcile_WhenAFiveFloorPlannerStoresASixthFloor_ReportsNoEntityFilterDrift() {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .title("Hidden Floor")
                .selectedKeywords(Set.of("Sinking"))
                .content(SIX_FLOOR_CONTENT)
                .published(true)
                .save(plannerSeeds);
        statsRepository.save(PlannerStats.builder().plannerId(planner.id()).build());
        FilterRowsSync.readCommitted(transactionManager)
                .executeWithoutResult(status -> catalogService.onBecameVisible(planner,
                        new PlannerPublishValidator().requirePublishable(planner), (Publication.Published) planner.publication()));

        assertThat(kindsFor(reconciler.reconcile(), planner.id())).doesNotContain("entity_filter");
    }

    @Test
    void reconcile_WhenARefractedRailwayPlannerIsIndexed_SkipsItsFilterAudit() {
        StoredPlanner planner = TestDataFactory.planner(owner)
                .title("Railway")
                .plannerType(PlannerType.REFRACTED_RAILWAY)
                .category("RR_PLACEHOLDER")
                .selectedKeywords(Set.of("Sinking"))
                .published(true)
                .save(plannerSeeds);
        statsRepository.save(PlannerStats.builder().plannerId(planner.id()).build());
        FilterRowsSync.readCommitted(transactionManager)
                .executeWithoutResult(status -> catalogService.onBecameVisible(planner,
                        new PlannerPublishValidator().requirePublishable(planner), (Publication.Published) planner.publication()));

        assertThat(kindsFor(reconciler.reconcile(), planner.id()))
                .doesNotContain("entity_filter", "keyword_filter");
    }

    @Test
    void reconcile_WhenTheKeywordColumnDisagreesWithTheContent_ReportsContentKeywordDrift() {
        StoredPlanner planner = publishClean("Keyword Column Drift", Set.of("Sinking"));
        jdbc.update("UPDATE planner_content SET selected_keywords = '[\"Burst\"]' WHERE planner_id = UUID_TO_BIN(?)",
                planner.id().toString());

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(recordsFor(records, planner.id(), "content_keywords"))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.expected()).isEqualTo("[Sinking]");
                    assertThat(record.actual()).isEqualTo("[Burst]");
                });
        assertThat(jdbc.queryForObject("SELECT selected_keywords FROM planner_content WHERE planner_id = UUID_TO_BIN(?)",
                String.class, planner.id().toString()))
                .as("the audit repairs nothing").isEqualTo("[\"Burst\"]");
    }

    @Test
    void reconcile_WhenADraftKeywordColumnDisagreesWithItsContent_ReportsContentKeywordDrift() {
        StoredPlanner draft = TestDataFactory.planner(owner).title("Draft Keyword Drift")
                .selectedKeywords(Set.of("Sinking"))
                .save(plannerSeeds);
        jdbc.update("UPDATE planner_content SET selected_keywords = NULL WHERE planner_id = UUID_TO_BIN(?)",
                draft.id().toString());

        assertThat(recordsFor(reconciler.reconcile(), draft.id(), "content_keywords"))
                .singleElement()
                .satisfies(record -> assertThat(record.actual()).isEqualTo("[]"));
    }

    @Test
    void reconcile_WhenTheKeywordIndexMatchesTheColumnButNotTheContent_ReportsKeywordFilterDrift() {
        StoredPlanner planner = publishClean("Index Follows Column", Set.of("Sinking"));
        jdbc.update("UPDATE planner_content SET selected_keywords = '[\"Burst\"]' WHERE planner_id = UUID_TO_BIN(?)",
                planner.id().toString());
        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());

        assertThat(recordsFor(reconciler.reconcile(), planner.id(), "keyword_filter"))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.expected()).isEqualTo("[Sinking]");
                    assertThat(record.actual()).isEqualTo("[Burst]");
                });
    }

    @Autowired
    private ObjectMapper objectMapper;

    private Set<String> indexedEntities(UUID plannerId) {
        return Set.copyOf(jdbc.queryForList("SELECT CONCAT(entity_type, ':', entity_id) FROM planner_entity_filter "
                + "WHERE planner_id = UUID_TO_BIN(?)", String.class, plannerId.toString()));
    }

    private Map<String, Object> storedContentRow(UUID plannerId) {
        return jdbc.queryForMap("SELECT content, sync_version FROM planner_content WHERE planner_id = UUID_TO_BIN(?)",
                plannerId.toString());
    }

    @Test
    void save_WhenTheFilterRebuildFails_RollsTheWholeSaveBack() throws Exception {
        StoredPlanner planner = publishClean("Rebuild Fails", Set.of());
        UUID plannerId = planner.id();
        Set<String> indexedBefore = indexedEntities(plannerId);
        Map<String, Object> storedBefore = storedContentRow(plannerId);
        StoredPlanner loaded = plannerRepository.findAggregateForOwner(plannerId, owner.getId()).orElseThrow();
        ObjectNode edited = (ObjectNode) objectMapper.readTree(loaded.content().json());
        ((ObjectNode) edited.get("equipment").get("1").get("identity")).put("id", "10102");
        UpsertPlannerRequest save = UpsertRequests.resending(loaded)
                .status(PlannerStatus.SAVED)
                .content(objectMapper.writeValueAsString(edited))
                .build();
        Object procedure = jdbc.queryForMap("SHOW CREATE PROCEDURE rebuild_planner_filters_scoped")
                .get("Create Procedure");
        assertThat(procedure).as("procedure body readable for restore").isNotNull();

        jdbc.execute("DROP PROCEDURE rebuild_planner_filters_scoped");
        try {
            assertThatThrownBy(() -> commandService.upsertPlanner(owner.getId(), UUID.randomUUID(), plannerId, save, WriteMode.CHECKED))
                    .isInstanceOf(DataAccessException.class)
                    .hasStackTraceContaining("rebuild_planner_filters_scoped does not exist");
        } finally {
            jdbc.execute(procedure.toString());
        }

        assertThat(storedContentRow(plannerId))
                .as("the failed rebuild rolled back the content and the version bump with it")
                .isEqualTo(storedBefore);
        assertThat(indexedEntities(plannerId)).isEqualTo(indexedBefore);
    }
}

package org.danteplanner.backend.integration;

import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerSubscriptionRepository;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerDriftReconciler;
import org.danteplanner.backend.planner.service.PlannerDriftReconciler.DriftRecord;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.LockWaits;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.exception.AccountDeletedException;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.support.FilterRowsSync;
import org.danteplanner.backend.support.PlannerSeeds;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.danteplanner.backend.support.Rejections.assertRejected;

/**
 * A deactivated owner's published planners are invisible to public reads and to the drift
 * audit's catalog membership for the grace window, and visible again on reactivation.
 */
class DeactivatedOwnerVisibilityIT extends ExclusiveDatabaseSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerSeeds plannerSeeds;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerCatalogService catalogService;

    @Autowired
    private PlannerAccessGuard accessGuard;

    @Autowired
    private UserAccountLifecycleService lifecycleService;

    @Autowired
    private PlannerDriftReconciler reconciler;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PlannerPublishingService publishingService;

    @Autowired
    private PlannerSubscriptionRepository subscriptionRepository;

    private User owner;
    private StoredPlanner planner;

    @BeforeEach
    void setUp() {
        owner = TestDataFactory.createTestUser(userRepository, "deactivated-owner@example.com");
        planner = TestDataFactory.createTestPlanner(plannerSeeds, owner, true);
        statsRepository.save(PlannerStats.builder().plannerId(planner.id()).build());
        TestDataFactory.addToCatalog(catalogService, planner);
    }

    private Integer filterRows(UUID plannerId) {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT (SELECT COUNT(*) FROM planner_entity_filter WHERE planner_id = UUID_TO_BIN(?))"
                        + " + (SELECT COUNT(*) FROM planner_keyword_filter WHERE planner_id = UUID_TO_BIN(?))",
                Integer.class, plannerId.toString(), plannerId.toString());
    }

    private Integer catalogRows(UUID plannerId) {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM planner_catalog WHERE planner_id = UUID_TO_BIN(?)",
                Integer.class, plannerId.toString());
    }

    private List<DriftRecord> membershipRecordsFor(List<DriftRecord> records, UUID plannerId) {
        return records.stream()
                .filter(r -> r.plannerId().equals(plannerId) && r.kind().equals("catalog_membership"))
                .toList();
    }

    @Test
    void publishedDetail_WhenOwnerActive_Returns200() throws Exception {
        mockMvc.perform(get("/api/planner/md/published/{id}", planner.id()))
                .andExpect(status().isOk());
    }

    @Test
    void publishedDetail_WhenOwnerDeactivated_Returns404PlannerNotFound() throws Exception {
        lifecycleService.deleteAccount(owner.getId());

        mockMvc.perform(get("/api/planner/md/published/{id}", planner.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
    }

    @Test
    void publishedDetail_WhenOwnerReactivated_Returns200() throws Exception {
        lifecycleService.deleteAccount(owner.getId());
        lifecycleService.reactivateAccount(owner.getId());

        mockMvc.perform(get("/api/planner/md/published/{id}", planner.id()))
                .andExpect(status().isOk());
    }

    @Test
    void checkPublished_WhenOwnerDeactivated_ThrowsPlannerNotFound() {
        lifecycleService.deleteAccount(owner.getId());

        assertRejected(() -> accessGuard.checkPublished(planner.id()))
                .withCode("PLANNER_NOT_FOUND");
    }

    @Test
    void reconcile_WhenOwnerDeactivated_ReportsNoMissingCatalogRow() {
        lifecycleService.deleteAccount(owner.getId());

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(membershipRecordsFor(records, planner.id()))
                .as("a withdrawn row of a deactivated owner is not membership drift")
                .isEmpty();
        assertThat(catalogRows(planner.id())).isZero();
    }

    @Test
    void reconcile_WhenDeactivatedOwnerKeepsCatalogRow_ReportsRowPresent() {
        lifecycleService.deleteAccount(owner.getId());
        TestDataFactory.addToCatalog(catalogService, planner);

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(membershipRecordsFor(records, planner.id()))
                .extracting(DriftRecord::expected, DriftRecord::actual)
                .containsExactly(tuple("row absent", "row present"));
    }

    @Test
    void commentTree_WhenOwnerDeactivated_Returns404PlannerNotFound() throws Exception {
        lifecycleService.deleteAccount(owner.getId());

        mockMvc.perform(get("/api/planner/{id}/comments", planner.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Planner not found with id: " + planner.id()));
    }

    @Test
    void commentTree_WhenOwnerReactivated_Returns200() throws Exception {
        lifecycleService.deleteAccount(owner.getId());
        lifecycleService.reactivateAccount(owner.getId());

        mockMvc.perform(get("/api/planner/{id}/comments", planner.id()))
                .andExpect(status().isOk());
    }

    @Test
    void deleteAccount_WhenCommitted_ClearsTheFilterRowsAndReactivationRebuildsThem() {
        FilterRowsSync.syncFilterRows(transactionManager, catalogService, planner.id());
        int built = filterRows(planner.id());
        assertThat(built).isPositive();

        lifecycleService.deleteAccount(owner.getId());

        assertThat(filterRows(planner.id()))
                .as("the deactivation's own soft delete hides the planner from the clear's visibility check")
                .isZero();

        lifecycleService.reactivateAccount(owner.getId());

        assertThat(filterRows(planner.id())).isEqualTo(built);
    }

    @Test
    void lockContentOwnedBy_WhenExplained_ReadsTheOwnersPlannersThroughTheUserIndex() throws Exception {
        TestDataFactory.createTestPlanner(plannerSeeds, owner, false);
        String sql = PlannerRepository.class.getMethod("lockContentOwnedBy", Long.class)
                .getAnnotation(Query.class).value()
                .replace(":userId", String.valueOf(owner.getId()));

        List<Map<String, Object>> plan = new JdbcTemplate(dataSource).queryForList("EXPLAIN " + sql);
        System.out.println("lockContentOwnedBy plan: " + plan);

        assertThat(plan).as("plan %s", plan)
                .filteredOn(row -> "p".equals(row.get("table")))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("type")).as("planner is read by user_id, not scanned").isEqualTo("ref");
                    assertThat(row.get("key")).isNotNull();
                });
        assertThat(plan).as("plan %s", plan)
                .filteredOn(row -> "c".equals(row.get("table")))
                .singleElement()
                .satisfies(row -> assertThat(row.get("type")).as("each content row by its key").isEqualTo("eq_ref"));
    }

    private boolean published(UUID plannerId) {
        return Boolean.TRUE.equals(new JdbcTemplate(dataSource).queryForObject(
                "SELECT published FROM planner_publication WHERE planner_id = UUID_TO_BIN(?)",
                Boolean.class, plannerId.toString()));
    }

    private void assertRaceNeverLeavesAPublishedPlannerVisible(String holderLock, Object holderKey) throws Exception {
        StoredPlanner draft = TestDataFactory.createTestPlanner(plannerSeeds, owner, false);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                subscriptionRepository.insertIfAbsent(owner.getId(), draft.id(), Instant.now()));
        CompletableFuture<Void> deactivation;
        CompletableFuture<Void> publication;

        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement(holderLock)) {
                lock.setObject(1, holderKey);
                lock.executeQuery().close();
            }

            deactivation = CompletableFuture.runAsync(() -> lifecycleService.deleteAccount(owner.getId()));
            LockWaits.await(new JdbcTemplate(dataSource), 1, deactivation::isDone,
                    "the deactivation queueing on the holder's lock");
            assertThat(deactivation).as("the deactivation must be held behind the holder's lock").isNotDone();

            publication = CompletableFuture.runAsync(() -> publishingService.publish(owner.getId(), draft.id()));
            LockWaits.await(new JdbcTemplate(dataSource), 2, publication::isDone,
                    "the publication queueing on the holder's lock");

            holder.commit();
        }

        Hang.get(deactivation, "the deactivation after the holder commits");
        Throwable publishFailure = null;
        try {
            Hang.get(publication, "the publication after the holder commits");
        } catch (ExecutionException e) {
            publishFailure = e.getCause();
        }
        if (!published(draft.id())) {
            assertThat(publishFailure).isInstanceOf(AccountDeletedException.class);
        }

        mockMvc.perform(get("/api/planner/md/published").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(draft.id().toString()))));
        mockMvc.perform(get("/api/planner/md/published/{id}", draft.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
    }

    @Test
    void publish_WhenRacingDeactivationBeforeItsIdRead_NeverLeavesAPublishedPlannerVisible() throws Exception {
        assertRaceNeverLeavesAPublishedPlannerVisible(
                "SELECT id FROM users WHERE id = ? FOR UPDATE", owner.getId());
    }

    @Test
    void publish_WhenRacingDeactivationAfterItsIdRead_NeverLeavesAPublishedPlannerVisible() throws Exception {
        assertRaceNeverLeavesAPublishedPlannerVisible(
                "SELECT planner_id FROM planner_catalog WHERE planner_id = UUID_TO_BIN(?) FOR UPDATE",
                planner.id().toString());
    }

    @Test
    void publish_WhenOwnerDeactivated_IsRejected() {
        StoredPlanner draft = TestDataFactory.createTestPlanner(plannerSeeds, owner, false);
        lifecycleService.deleteAccount(owner.getId());

        assertRejected(() -> publishingService.publish(owner.getId(), draft.id()))
                .withCode("UNAUTHORIZED")
                .satisfies(rejection -> assertThat(rejection.getMessage()).isEqualTo("Account with ID " + owner.getId() + " has been deleted"));
        assertThat(published(draft.id())).isFalse();
    }
}

package org.danteplanner.backend.integration;

import net.javacrumbs.shedlock.core.LockProvider;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerSubscription;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerSubscriptionRepository;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerDriftReconciler;
import org.danteplanner.backend.planner.service.PlannerDriftReconciler.DriftRecord;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.exception.AccountDeletedException;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A deactivated owner's published planners are invisible to public reads and to the drift
 * audit's catalog membership for the grace window, and visible again on reactivation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import({TestConfig.class, DeactivatedOwnerVisibilityIT.GrantedLockHarness.class})
class DeactivatedOwnerVisibilityIT extends SharedMySqlContainerSupport {

    @TestConfiguration
    static class GrantedLockHarness {

        @Bean
        @Primary
        LockProvider grantedLockProvider() {
            return configuration -> Optional.of(() -> { });
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

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
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PlannerPublishingService publishingService;

    @Autowired
    private PlannerSubscriptionRepository subscriptionRepository;

    private User owner;
    private Planner planner;

    @BeforeEach
    void setUp() {
        owner = TestDataFactory.createTestUser(userRepository, "deactivated-owner@example.com");
        planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
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
        mockMvc.perform(get("/api/planner/md/published/{id}", planner.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void publishedDetail_WhenOwnerDeactivated_Returns404PlannerNotFound() throws Exception {
        lifecycleService.deleteAccount(owner.getId());

        mockMvc.perform(get("/api/planner/md/published/{id}", planner.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
    }

    @Test
    void publishedDetail_WhenOwnerReactivated_Returns200() throws Exception {
        lifecycleService.deleteAccount(owner.getId());
        lifecycleService.reactivateAccount(owner.getId());

        mockMvc.perform(get("/api/planner/md/published/{id}", planner.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void checkPublished_WhenOwnerDeactivated_ThrowsPlannerNotFound() {
        lifecycleService.deleteAccount(owner.getId());

        assertThatThrownBy(() -> accessGuard.checkPublished(planner.getId()))
                .isInstanceOf(PlannerNotFoundException.class);
    }

    @Test
    void reconcile_WhenOwnerDeactivated_ReportsNoMissingCatalogRow() {
        lifecycleService.deleteAccount(owner.getId());

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(membershipRecordsFor(records, planner.getId()))
                .as("a withdrawn row of a deactivated owner is not membership drift")
                .isEmpty();
        assertThat(catalogRows(planner.getId())).isZero();
    }

    @Test
    void reconcile_WhenDeactivatedOwnerKeepsCatalogRow_ReportsRowPresent() {
        lifecycleService.deleteAccount(owner.getId());
        catalogService.add(planner);

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(membershipRecordsFor(records, planner.getId()))
                .extracting(DriftRecord::expected, DriftRecord::actual)
                .containsExactly(tuple("row absent", "row present"));
    }

    private boolean withdrawalMarked(UUID plannerId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey("del:published-planner:" + plannerId));
    }

    @Test
    void commentTree_WhenOwnerDeactivated_Returns404PlannerNotFound() throws Exception {
        lifecycleService.deleteAccount(owner.getId());

        mockMvc.perform(get("/api/planner/{id}/comments", planner.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Planner not found with id: " + planner.getId()));
    }

    @Test
    void commentTree_WhenOwnerReactivated_Returns200() throws Exception {
        lifecycleService.deleteAccount(owner.getId());
        lifecycleService.reactivateAccount(owner.getId());

        mockMvc.perform(get("/api/planner/{id}/comments", planner.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void deleteAccount_WhenCommitted_MarksEachPublishedPlannerWithdrawn() {
        Planner draft = TestDataFactory.createTestPlanner(plannerRepository, owner, false);

        lifecycleService.deleteAccount(owner.getId());

        assertThat(withdrawalMarked(planner.getId())).isTrue();
        assertThat(withdrawalMarked(draft.getId())).isFalse();
    }

    @Test
    void deleteAccount_WhenRolledBack_WritesNoWithdrawalMarker() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            lifecycleService.deleteAccount(owner.getId());
            status.setRollbackOnly();
        });

        assertThat(withdrawalMarked(planner.getId())).isFalse();
    }

    @Test
    void deleteAccount_WhenOwnerHasNoPublishedPlanner_Succeeds() {
        User empty = TestDataFactory.createTestUser(userRepository, "deactivated-owner-empty@example.com");
        User draftsOnly = TestDataFactory.createTestUser(userRepository, "deactivated-owner-drafts@example.com");
        Planner draft = TestDataFactory.createTestPlanner(plannerRepository, draftsOnly, false);

        lifecycleService.deleteAccount(empty.getId());
        lifecycleService.deleteAccount(draftsOnly.getId());
        lifecycleService.reactivateAccount(empty.getId());
        lifecycleService.reactivateAccount(draftsOnly.getId());

        assertThat(withdrawalMarked(draft.getId())).isFalse();
    }

    @Test
    void reactivateAccount_WhenCommitted_ClearsTheWithdrawalMarkers() {
        lifecycleService.deleteAccount(owner.getId());
        assertThat(withdrawalMarked(planner.getId())).isTrue();

        lifecycleService.reactivateAccount(owner.getId());

        assertThat(withdrawalMarked(planner.getId())).isFalse();
    }

    private int lockWaits() {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_state = 'LOCK WAIT'", Integer.class);
    }

    private void awaitCondition(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (!condition.getAsBoolean()) {
            assertThat(Instant.now()).as("condition not reached before the deadline").isBefore(deadline);
            TimeUnit.MILLISECONDS.sleep(250);
        }
    }

    private boolean published(UUID plannerId) {
        return Boolean.TRUE.equals(new JdbcTemplate(dataSource).queryForObject(
                "SELECT published FROM planner_publication WHERE planner_id = UUID_TO_BIN(?)",
                Boolean.class, plannerId.toString()));
    }

    private void assertRaceNeverLeavesAPublishedPlannerUnmarked(String holderLock, Object holderKey) throws Exception {
        Planner draft = TestDataFactory.createTestPlanner(plannerRepository, owner, false);
        subscriptionRepository.insert(new PlannerSubscription(owner.getId(), draft.getId()));
        CompletableFuture<Void> deactivation;
        CompletableFuture<Void> publication;

        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement(holderLock)) {
                lock.setObject(1, holderKey);
                lock.executeQuery().close();
            }

            deactivation = CompletableFuture.runAsync(() -> lifecycleService.deleteAccount(owner.getId()));
            awaitCondition(() -> deactivation.isDone() || lockWaits() >= 1);
            assertThat(deactivation).as("the deactivation must be held behind the holder's lock").isNotDone();

            publication = CompletableFuture.runAsync(() -> publishingService.publish(owner.getId(), draft.getId()));
            awaitCondition(() -> publication.isDone() || lockWaits() >= 2);

            holder.commit();
        }

        deactivation.get(30, TimeUnit.SECONDS);
        Throwable publishFailure = null;
        try {
            publication.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            publishFailure = e.getCause();
        }

        if (published(draft.getId())) {
            assertThat(withdrawalMarked(draft.getId()))
                    .as("a planner published around its owner's deactivation must carry the withdrawal marker")
                    .isTrue();
        } else {
            assertThat(publishFailure).isInstanceOf(AccountDeletedException.class);
        }
    }

    @Test
    void publish_WhenRacingDeactivationBeforeItsIdRead_NeverLeavesAPublishedPlannerUnmarked() throws Exception {
        assertRaceNeverLeavesAPublishedPlannerUnmarked(
                "SELECT id FROM users WHERE id = ? FOR UPDATE", owner.getId());
    }

    @Test
    void publish_WhenRacingDeactivationAfterItsIdRead_NeverLeavesAPublishedPlannerUnmarked() throws Exception {
        assertRaceNeverLeavesAPublishedPlannerUnmarked(
                "SELECT planner_id FROM planner_catalog WHERE planner_id = UUID_TO_BIN(?) FOR UPDATE",
                planner.getId().toString());
    }

    @Test
    void publish_WhenOwnerDeactivated_IsRejected() {
        Planner draft = TestDataFactory.createTestPlanner(plannerRepository, owner, false);
        lifecycleService.deleteAccount(owner.getId());

        assertThatThrownBy(() -> publishingService.publish(owner.getId(), draft.getId()))
                .isInstanceOf(AccountDeletedException.class);
        assertThat(published(draft.getId())).isFalse();
    }
}

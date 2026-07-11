package org.danteplanner.backend.integration;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.service.PlannerQueryService;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Phase-3 acceptance test (INV1): a replica {@code byId} miss re-checks the primary before
 * answering.
 *
 * <p>With replication paused, a planner row is written to the primary that has not replicated.
 * A read through the {@link ByIdReadGuard} seam wraps {@link PlannerQueryService#getPlanner}, which
 * is {@code @Transactional(readOnly = true)} and therefore routes to the stale replica — a genuine
 * miss. The contract: the seam re-checks the primary, finds the entity, serves it, and increments
 * {@code replica_miss_promoted_total}. The seam is a pass-through today, so the miss surfaces as a
 * {@link org.danteplanner.backend.planner.exception.PlannerNotFoundException} where a promotion was
 * expected.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class ReplicaLagIT extends CausalHarnessSupport {

    private static final String PROMOTED_COUNTER = "replica_miss_promoted_total";

    private static final String PIN_PROBE_TABLE = "pin_leak_probe";
    private static final String STALE_PROBE_VALUE = "replicated-stale";
    private static final String FRESH_PROBE_VALUE = "primary-only-fresh";

    @Autowired
    private ByIdReadGuard byIdReadGuard;

    @Autowired
    private PlannerQueryService plannerQueryService;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void routingProperties(DynamicPropertyRegistry registry) {
        registry.add("datasource.routing.enabled", () -> "true");
        registry.add("datasource.replica.enabled", () -> "true");
        registry.add("datasource.replica.url", REPLICA::getJdbcUrl);
        registry.add("datasource.replica.username", REPLICA::getUsername);
        registry.add("datasource.replica.password", REPLICA::getPassword);
    }

    @Test
    @DisplayName("INV1: a replica byId miss re-checks the primary, serves the entity, and increments the promotion counter")
    void byIdMissOnPausedReplica_reChecksPrimary_servesEntityAndIncrementsCounter() {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-it@example.com");
        Long userId = owner.getId();
        replicationControl.awaitCaughtUp();

        try {
            replicationControl.stopReplica();

            Planner primaryOnly = TestDataFactory.createTestPlanner(plannerRepository, owner, false);
            UUID plannerId = primaryOnly.getId();

            double before = promotedCount();

            AtomicReference<PlannerResponse> served = new AtomicReference<>();
            Throwable thrown = catchThrowable(() -> served.set(byIdReadGuard.read(
                    ByIdReadGuard.PLANNER_ENTITY_TYPE,
                    plannerId,
                    () -> plannerQueryService.getPlanner(userId, plannerId))));

            assertThat(thrown)
                    .as("a replica byId miss must be re-checked on the primary and promoted, not surfaced as a miss")
                    .isNull();
            assertThat(served.get())
                    .as("the promoted re-check must serve the primary entity")
                    .isNotNull()
                    .extracting(PlannerResponse::id)
                    .isEqualTo(plannerId);
            assertThat(promotedCount() - before)
                    .as("a promoted miss must increment " + PROMOTED_COUNTER + " by 1")
                    .isEqualTo(1.0);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    @DisplayName("INV1 negative: a byId absent on both replica and primary propagates PlannerNotFoundException, does not promote, and clears the BULKHEAD pin so a follow-on read-only read still routes to the replica")
    void byIdMissOnBothReplicaAndPrimary_propagatesNotFound_doesNotPromote_andClearsPin() {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-doublemiss@example.com");
        Long userId = owner.getId();
        replicationControl.awaitCaughtUp();

        try {
            primaryJdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS " + PIN_PROBE_TABLE + " (id INT PRIMARY KEY, val VARCHAR(64))");
            primaryJdbcTemplate.update(
                    "REPLACE INTO " + PIN_PROBE_TABLE + " (id, val) VALUES (1, ?)", STALE_PROBE_VALUE);
            replicationControl.awaitCaughtUp();

            replicationControl.stopReplica();
            primaryJdbcTemplate.update(
                    "UPDATE " + PIN_PROBE_TABLE + " SET val = ? WHERE id = 1", FRESH_PROBE_VALUE);

            UUID absentEverywhere = UUID.randomUUID();
            double before = promotedCount();

            Throwable thrown = catchThrowable(() -> byIdReadGuard.read(
                    ByIdReadGuard.PLANNER_ENTITY_TYPE,
                    absentEverywhere,
                    () -> plannerQueryService.getPlanner(userId, absentEverywhere)));

            assertThat(thrown)
                    .as("a byId absent on both replica and primary must propagate the miss as a 404")
                    .isInstanceOf(org.danteplanner.backend.planner.exception.PlannerNotFoundException.class);
            assertThat(promotedCount() - before)
                    .as("a double-miss must NOT increment " + PROMOTED_COUNTER)
                    .isEqualTo(0.0);
            assertThat(readProbeViaRouting())
                    .as("the BULKHEAD pin must be cleared in a finally, so a follow-on read-only read on the same thread still routes to the stale replica")
                    .isEqualTo(STALE_PROBE_VALUE);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    @DisplayName("INV1 replica hit: a byId present on the replica is served without a re-check and leaves the promotion counter unchanged")
    void byIdHitOnReplica_servesWithoutReCheck_andDoesNotPromote() {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-hit@example.com");
        Long userId = owner.getId();
        replicationControl.awaitCaughtUp();

        Planner replicated = TestDataFactory.createTestPlanner(plannerRepository, owner, false);
        UUID plannerId = replicated.getId();
        replicationControl.awaitCaughtUp();

        double before = promotedCount();

        AtomicReference<PlannerResponse> served = new AtomicReference<>();
        Throwable thrown = catchThrowable(() -> served.set(byIdReadGuard.read(
                ByIdReadGuard.PLANNER_ENTITY_TYPE,
                plannerId,
                () -> plannerQueryService.getPlanner(userId, plannerId))));

        assertThat(thrown)
                .as("a replica hit must not surface as a miss")
                .isNull();
        assertThat(served.get())
                .as("a replica hit must serve the entity directly")
                .isNotNull()
                .extracting(PlannerResponse::id)
                .isEqualTo(plannerId);
        assertThat(promotedCount() - before)
                .as("a replica hit must NOT increment " + PROMOTED_COUNTER)
                .isEqualTo(0.0);
    }

    private String readProbeViaRouting() {
        TransactionTemplate tt = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        tt.setReadOnly(true);
        return tt.execute(status ->
                new JdbcTemplate(dataSource).queryForObject(
                        "SELECT val FROM " + PIN_PROBE_TABLE + " WHERE id = 1", String.class));
    }

    private double promotedCount() {
        Counter counter = meterRegistry.find(PROMOTED_COUNTER).counter();
        return counter == null ? 0.0 : counter.count();
    }
}

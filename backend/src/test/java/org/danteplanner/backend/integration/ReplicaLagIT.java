package org.danteplanner.backend.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.moderation.service.PlannerModerationService;
import org.danteplanner.backend.planner.dto.PlannerBatchRequest;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.danteplanner.backend.planner.service.PlannerQueryService;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.security.CsrfDoubleSubmitFilter;
import org.danteplanner.backend.shared.security.CsrfTokenService;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import org.danteplanner.backend.shared.exception.EntityNotFoundException;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;

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
    private PlannerCommandService plannerCommandService;

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
    @Qualifier("replicaJdbcTemplate")
    private JdbcTemplate replicaJdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Autowired
    private PlannerModerationService plannerModerationService;

    @Autowired
    private PlannerPublishingService plannerPublishingService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private CsrfTokenService csrfTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountLifecycleService userAccountLifecycleService;

    @LocalServerPort
    private int port;

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
    void byIdMissOnPausedReplica_WhenReChecksPrimary_ServesEntityAndIncrementsCounter() {
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
    void byIdMissOnBothReplicaAndPrimary_WhenPropagatesNotFound_DoesNotPromote_AndClearsPin() {
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
                    .isInstanceOf(PlannerNotFoundException.class);
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
    void byIdHitOnReplica_WhenServesWithoutReCheck_AndDoesNotPromote() {
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

    @Test
    @DisplayName("INV2: a delete on the primary while replication is paused makes a byId via the stale replica return 404, even though the replica still holds the non-soft-deleted row")
    void deleteTombstonesGhost_WhenReplicaPositive_Returns404() {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-tombstone@example.com");
        Long userId = owner.getId();

        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, false);
        UUID plannerId = planner.getId();
        replicationControl.awaitCaughtUp();

        try {
            replicationControl.stopReplica();

            plannerCommandService.deletePlanner(userId, plannerId);

            Timestamp replicaDeletedAt = replicaJdbcTemplate.queryForObject(
                    "SELECT deleted_at FROM planner_content WHERE planner_id = UUID_TO_BIN(?)",
                    Timestamp.class,
                    plannerId.toString());
            assertThat(replicaDeletedAt)
                    .as("the paused replica must still hold the row un-soft-deleted, so the 404 comes from the tombstone, not replication catching up")
                    .isNull();

            Throwable thrown = catchThrowable(() -> byIdReadGuard.read(
                    ByIdReadGuard.PLANNER_ENTITY_TYPE,
                    plannerId,
                    () -> plannerQueryService.getPlanner(userId, plannerId)));

            assertThat(thrown)
                    .as("a delete issues a tombstone synchronously before the response, so a replica-served positive whose del:planner:<id> is present must return 404")
                    .isInstanceOf(EntityNotFoundException.class);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    @DisplayName("INV2 write half: a delete issues a del:planner:<id> tombstone synchronously with a bounded ~1h TTL")
    void deleteOnPrimary_WhenWritesTombstoneKey_WithBoundedTtl() {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-tombstone-write@example.com");

        Planner p = TestDataFactory.createTestPlanner(plannerRepository, owner, false);
        UUID plannerId = p.getId();

        plannerCommandService.deletePlanner(owner.getId(), plannerId);

        String key = "del:planner:" + plannerId;

        assertThat(stringRedisTemplate.hasKey(key))
                .as("a delete must issue the tombstone " + key + " synchronously before returning")
                .isTrue();

        Long ttl = stringRedisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(ttl)
                .as("the tombstone must carry a bounded ~1h TTL (PX 3600000), not persist forever")
                .isGreaterThan(0L)
                .isLessThanOrEqualTo(3600L);
    }

    @Test
    @DisplayName("INV2 published read: an owner soft-delete on the primary masks the published read from the paused replica with the tombstone's 404")
    void publishedRead_WhenOwnerSoftDeletesOnPausedReplica_Returns404FromTheTombstone() throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-published-delete@example.com");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        UUID plannerId = planner.getId();
        replicationControl.awaitCaughtUp();

        try {
            replicationControl.stopReplica();

            plannerCommandService.deletePlanner(owner.getId(), plannerId);

            assertThat(replicaStillPublished(plannerId))
                    .as("the paused replica must still hold the planner published and live")
                    .isTrue();
            assertMaskedLikeASoftDelete(getPublished(plannerId), plannerId);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"moderator-takedown", "moderator-unpublish", "owner-unpublish"})
    @DisplayName("INV2 published read: a planner withdrawn from public view on the primary answers on the paused replica the 404 a caught-up replica gives")
    void publishedRead_WhenWithdrawnFromPublicViewOnPausedReplica_Returns404LikeACaughtUpReplica(String withdrawal)
            throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-withdraw-" + withdrawal + "@example.com");
        User moderator = TestDataFactory.createModerator(userRepository, "replica-lag-withdraw-mod-" + withdrawal + "@example.com");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        UUID plannerId = planner.getId();
        replicationControl.awaitCaughtUp();

        try {
            replicationControl.stopReplica();

            withdraw(withdrawal, owner, moderator, plannerId);

            assertThat(replicaStillPublished(plannerId))
                    .as("the paused replica must still hold the planner published, so a 404 comes from the read path, not replication")
                    .isTrue();
            assertNotFoundLikeACaughtUpReplica(getPublished(plannerId), plannerId);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"moderator-takedown", "moderator-unpublish", "owner-unpublish"})
    @DisplayName("INV2 owner read: withdrawing a planner from public view leaves the owner's own by-id read served")
    void ownerRead_WhenWithdrawnFromPublicViewOnPausedReplica_StillServesTheOwner(String withdrawal) {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-owner-read-" + withdrawal + "@example.com");
        User moderator = TestDataFactory.createModerator(userRepository, "replica-lag-owner-read-mod-" + withdrawal + "@example.com");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        UUID plannerId = planner.getId();
        Long userId = owner.getId();
        replicationControl.awaitCaughtUp();

        try {
            replicationControl.stopReplica();

            withdraw(withdrawal, owner, moderator, plannerId);

            PlannerResponse served = byIdReadGuard.read(ByIdReadGuard.PLANNER_ENTITY_TYPE, plannerId,
                    () -> plannerQueryService.getPlanner(userId, plannerId));
            assertThat(served.id())
                    .as("a planner out of public view is still the owner's planner")
                    .isEqualTo(plannerId);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    @DisplayName("INV2 published read: a planner unpublished and republished on the primary is served while the replica is paused")
    void publishedRead_WhenRepublishedOnPausedReplica_ServesThePlanner() throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-republish@example.com");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        UUID plannerId = planner.getId();
        replicationControl.awaitCaughtUp();

        try {
            replicationControl.stopReplica();

            plannerPublishingService.unpublish(owner.getId(), plannerId);
            plannerPublishingService.publish(owner.getId(), plannerId);

            HttpResponse<String> response = getPublished(plannerId);
            assertThat(response.statusCode())
                    .as("a republished planner must be visible at once: " + response.body())
                    .isEqualTo(200);
            assertThat(objectMapper.readTree(response.body()).get("id").asText())
                    .isEqualTo(plannerId.toString());
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    @DisplayName("INV2 batch: a planner deleted on the primary is omitted from the batch pull while the paused replica still holds it")
    void batchPull_WhenAPlannerIsTombstoned_OmitsItWhileKeepingTheLiveOne() throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-batch-tombstone@example.com");
        Planner live = TestDataFactory.createTestPlanner(plannerRepository, owner, false);
        Planner doomed = TestDataFactory.createTestPlanner(plannerRepository, owner, false);
        replicationControl.awaitCaughtUp();

        try {
            replicationControl.stopReplica();

            plannerCommandService.deletePlanner(owner.getId(), doomed.getId());

            Timestamp replicaDeletedAt = replicaJdbcTemplate.queryForObject(
                    "SELECT deleted_at FROM planner_content WHERE planner_id = UUID_TO_BIN(?)",
                    Timestamp.class,
                    doomed.getId().toString());
            assertThat(replicaDeletedAt)
                    .as("the paused replica must still hold the row un-soft-deleted, so the omission comes from the tombstone")
                    .isNull();

            List<String> served = batchPull(owner, List.of(doomed.getId(), live.getId()));

            assertThat(served)
                    .as("the tombstoned planner must be masked and the live one served")
                    .containsExactly(live.getId().toString());
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    @DisplayName("INV1 batch: a planner the paused replica lacks is promoted from the primary into the owner's batch pull")
    void batchPull_WhenReplicaLacksAnOwnedPlanner_PromotesItFromThePrimary() throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-batch-miss@example.com");
        replicationControl.awaitCaughtUp();

        try {
            replicationControl.stopReplica();

            Planner primaryOnly = TestDataFactory.createTestPlanner(plannerRepository, owner, false);
            double before = promotedCount();

            List<String> served = batchPull(owner, List.of(primaryOnly.getId(), UUID.randomUUID()));

            assertThat(served)
                    .as("a batch-pull miss on the replica must be re-checked on the primary; an id absent there too stays omitted")
                    .containsExactly(primaryOnly.getId().toString());
            assertThat(promotedCount() - before)
                    .as("one promoted row must increment " + PROMOTED_COUNTER + " by 1")
                    .isEqualTo(1.0);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    @DisplayName("batch: ids that exist nowhere are omitted without promoting anything")
    void batchPull_WhenIdsExistNowhere_ReturnsAnEmptyArray() throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-batch-unknown@example.com");
        replicationControl.awaitCaughtUp();
        double before = promotedCount();

        List<String> served = batchPull(owner, List.of(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(served).isEmpty();
        assertThat(promotedCount() - before).isEqualTo(0.0);
    }

    private void withdraw(String withdrawal, User owner, User moderator, UUID plannerId) {
        switch (withdrawal) {
            case "moderator-takedown" -> plannerModerationService.deletePlanner(moderator.getId(), plannerId, "replica lag");
            case "moderator-unpublish" -> plannerModerationService.unpublishPlanner(moderator.getId(), plannerId);
            case "owner-unpublish" -> plannerPublishingService.unpublish(owner.getId(), plannerId);
            default -> throw new IllegalArgumentException(withdrawal);
        }
    }

    private boolean replicaStillPublished(UUID plannerId) {
        Boolean published = replicaJdbcTemplate.queryForObject(
                "SELECT published FROM planner_publication WHERE planner_id = UUID_TO_BIN(?)",
                Boolean.class,
                plannerId.toString());
        Timestamp deletedAt = replicaJdbcTemplate.queryForObject(
                "SELECT deleted_at FROM planner_content WHERE planner_id = UUID_TO_BIN(?)",
                Timestamp.class,
                plannerId.toString());
        return Boolean.TRUE.equals(published) && deletedAt == null;
    }

    private void assertMaskedLikeASoftDelete(HttpResponse<String> response, UUID plannerId) throws Exception {
        assertThat(response.statusCode())
                .as("the published read must be masked, not served: " + response.body())
                .isEqualTo(404);
        JsonNode problem = objectMapper.readTree(response.body());
        assertThat(problem.path("code").asText()).isEqualTo("NOT_FOUND");
        assertThat(problem.path("detail").asText()).isEqualTo("planner not found with id: " + plannerId);
    }

    @Test
    @DisplayName("INV2 published read: once a withdrawn planner is republished, a read on the caught-up replica issues no bulkhead query")
    void publishedRead_WhenRepublishedAfterWithdrawalOnCaughtUpReplica_IssuesNoBulkheadQuery() throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-republish-bulkhead@example.com");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        UUID plannerId = planner.getId();

        plannerPublishingService.unpublish(owner.getId(), plannerId);
        plannerPublishingService.publish(owner.getId(), plannerId);
        replicationControl.awaitCaughtUp();
        double before = bulkheadQueries();

        HttpResponse<String> response = getPublished(plannerId);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(bulkheadQueries() - before)
                .as("a republish must lift the withdrawal marker, so the caught-up replica's hit is served as-is")
                .isEqualTo(0.0);
    }

    private void assertNotFoundLikeACaughtUpReplica(HttpResponse<String> response, UUID plannerId) throws Exception {
        assertThat(response.statusCode())
                .as("the published read must be masked, not served: " + response.body())
                .isEqualTo(404);
        JsonNode problem = objectMapper.readTree(response.body());
        assertThat(problem.path("code").asText()).isEqualTo("PLANNER_NOT_FOUND");
        assertThat(problem.path("detail").asText()).isEqualTo("Planner not found with id: " + plannerId);
    }

    private double bulkheadQueries() {
        Counter counter = meterRegistry.find("replica_bulkhead_queries_total").counter();
        return counter == null ? 0.0 : counter.count();
    }

    private HttpResponse<String> getPublished(UUID plannerId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/planner/md/published/" + plannerId)).GET().build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private List<String> batchPull(User owner, List<UUID> ids) throws Exception {
        String accessToken = TestDataFactory.generateAccessToken(jwtTokenService, owner);
        String csrf = csrfTokenService.mint();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/planner/md/batch"))
                .header("Content-Type", "application/json")
                .header(CsrfDoubleSubmitFilter.CSRF_HEADER, csrf)
                .header("Cookie", CookieConstants.ACCESS_TOKEN + "=" + accessToken
                        + "; " + CookieConstants.CSRF + "=" + csrf)
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(new PlannerBatchRequest(ids))))
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            List<String> served = new ArrayList<>();
            for (JsonNode planner : objectMapper.readTree(response.body())) {
                served.add(planner.get("id").asText());
            }
            return served;
        }
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

    @Test
    @DisplayName("INV2 published read: a deactivated owner's planners answer on the paused replica the 404 a caught-up replica gives")
    void publishedRead_WhenOwnerDeactivatedOnPausedReplica_Returns404LikeACaughtUpReplica() throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-deactivate@example.com");
        Planner first = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        Planner second = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        replicationControl.awaitCaughtUp();

        try {
            replicationControl.stopReplica();

            userAccountLifecycleService.deleteAccount(owner.getId());

            Timestamp replicaOwnerDeletedAt = replicaJdbcTemplate.queryForObject(
                    "SELECT deleted_at FROM users WHERE id = ?", Timestamp.class, owner.getId());
            assertThat(replicaOwnerDeletedAt)
                    .as("the paused replica must still hold the owner active, so a 404 comes from the read path, not replication")
                    .isNull();
            for (Planner planner : List.of(first, second)) {
                assertThat(replicaStillPublished(planner.getId())).isTrue();
                assertNotFoundLikeACaughtUpReplica(getPublished(planner.getId()), planner.getId());
            }
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    @DisplayName("INV2 published read: once a deactivated owner reactivates, a read on the caught-up replica issues no bulkhead query")
    void publishedRead_WhenOwnerReactivatedOnCaughtUpReplica_IssuesNoBulkheadQuery() throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-reactivate@example.com");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);

        userAccountLifecycleService.deleteAccount(owner.getId());
        assertThat(stringRedisTemplate.hasKey("del:published-planner:" + planner.getId()))
                .as("the deactivation must have marked the planner, or clearing it proves nothing")
                .isTrue();
        userAccountLifecycleService.reactivateAccount(owner.getId());
        replicationControl.awaitCaughtUp();
        double before = bulkheadQueries();

        HttpResponse<String> response = getPublished(planner.getId());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(bulkheadQueries() - before)
                .as("a reactivation must lift the withdrawal markers, so the caught-up replica's hit is served as-is")
                .isEqualTo(0.0);
    }

    @Test
    @DisplayName("INV2 split reads: a deactivated owner's planner answers stats, flags and viewcount on the paused replica with the detail read's 404 and records no view")
    void splitReads_WhenOwnerDeactivatedOnPausedReplica_Return404AndRecordNoView() throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-split-owner@example.com");
        User viewer = TestDataFactory.createTestUser(userRepository, "replica-lag-split-viewer@example.com");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        UUID plannerId = planner.getId();
        replicationControl.awaitCaughtUp();

        try {
            replicationControl.stopReplica();

            userAccountLifecycleService.deleteAccount(owner.getId());

            assertThat(replicaStillPublished(plannerId)).isTrue();
            String base = "http://localhost:" + port + "/api/planner/md/published/" + plannerId;
            String viewerCookie = CookieConstants.ACCESS_TOKEN + "="
                    + TestDataFactory.generateAccessToken(jwtTokenService, viewer);
            assertNotFoundLikeACaughtUpReplica(send(HttpRequest.newBuilder(URI.create(base + "/stats")).GET()),
                    plannerId);
            assertNotFoundLikeACaughtUpReplica(send(HttpRequest.newBuilder(URI.create(base + "/flags")).GET()),
                    plannerId);
            assertNotFoundLikeACaughtUpReplica(send(HttpRequest.newBuilder(URI.create(base + "/flags"))
                    .header("Cookie", viewerCookie).GET()), plannerId);
            String csrf = csrfTokenService.mint();
            assertNotFoundLikeACaughtUpReplica(send(HttpRequest.newBuilder(URI.create(base + "/viewcount"))
                    .header(CsrfDoubleSubmitFilter.CSRF_HEADER, csrf)
                    .header("Cookie", CookieConstants.CSRF + "=" + csrf)
                    .POST(HttpRequest.BodyPublishers.noBody())), plannerId);

            assertThat(stringRedisTemplate.opsForHash().hasKey("views:buffer", plannerId.toString())).isFalse();
            assertThat(stringRedisTemplate.keys("views:seen:*:" + plannerId + ":*")).isEmpty();
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}

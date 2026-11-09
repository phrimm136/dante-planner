package org.danteplanner.backend.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.PlannerBatchRequest;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.config.PoolLedger;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.security.CsrfDoubleSubmitFilter;
import org.danteplanner.backend.shared.security.CsrfTokenService;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.ViewerHashUtil;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
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
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class ReplicaLagIT extends ReplicaRoutingHarnessSupport {

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
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private CsrfTokenService csrfTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    @ParameterizedTest
    @ValueSource(strings = {"moderator-takedown", "moderator-unpublish", "owner-unpublish"})
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

    @Test
    void publishedRead_WhenRepublishedOnPausedReplica_ServesThePlanner() throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-republish@example.com");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        UUID plannerId = planner.getId();
        replicationControl.awaitCaughtUp();

        try {
            replicationControl.stopReplica();

            send(owner, "POST", "/api/planner/md/" + plannerId + "/unpublish", null);
            send(owner, "POST", "/api/planner/md/" + plannerId + "/publish", null);

            HttpResponse<String> response = getPublished(plannerId);
            assertThat(response.statusCode())
                    .as("a republished planner must be visible at once: " + response.body())
                    .isEqualTo(200);
            assertThat(objectMapper.readTree(response.body()).get("id").asString())
                    .isEqualTo(plannerId.toString());
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST"})
    void batchPull_WhenAPlannerIsDeletedOnPausedReplica_OmitsItWhileKeepingTheLiveOne(String method) throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-batch-deleted-" + method + "@example.com");
        Planner live = TestDataFactory.createTestPlanner(plannerRepository, owner, false);
        Planner doomed = TestDataFactory.createTestPlanner(plannerRepository, owner, false);
        replicationControl.awaitCaughtUp();

        try {
            replicationControl.stopReplica();

            deletePlanner(owner, doomed.getId());

            Timestamp replicaDeletedAt = replicaJdbcTemplate.queryForObject(
                    "SELECT deleted_at FROM planner_content WHERE planner_id = UUID_TO_BIN(?)",
                    Timestamp.class,
                    doomed.getId().toString());
            assertThat(replicaDeletedAt)
                    .as("the paused replica must still hold the row un-soft-deleted, so the omission comes from the read path")
                    .isNull();

            List<String> served = batchPull(method, owner, List.of(doomed.getId(), live.getId()));

            assertThat(served)
                    .as("the deleted planner must be omitted and the live one served")
                    .containsExactly(live.getId().toString());
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST"})
    void batchPull_WhenIdsExistNowhereAndCarryNoMarker_ReturnsAnEmptyArrayWithoutReadingThePrimary(String method)
            throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-batch-unknown-" + method + "@example.com");
        replicationControl.awaitCaughtUp();
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        long before = primaryReadAcquisitions();

        List<String> served = batchPull(method, owner, ids);

        assertThat(served).isEmpty();
        assertThat(primaryReadAcquisitions() - before)
                .as("a miss whose scopes carry no marker is final on the replica, so the primary-read pool lends nothing")
                .isZero();
    }

    private long primaryReadAcquisitions() {
        Timer acquire = meterRegistry.find("hikaricp.connections.acquire")
                .tag("pool", PoolLedger.PRIMARY_READ_POOL_NAME)
                .timer();
        assertThat(acquire).as("the Seoul context builds the primary-read pool").isNotNull();
        return acquire.count();
    }

    private void withdraw(String withdrawal, User owner, User moderator, UUID plannerId) throws Exception {
        switch (withdrawal) {
            case "moderator-takedown" -> send(moderator, "POST", "/api/moderation/planner/" + plannerId + "/takedown",
                    "{\"reason\":\"replica lag\"}");
            case "moderator-unpublish" -> send(moderator, "PUT", "/api/moderation/planner/" + plannerId + "/unpublish",
                    null);
            case "owner-unpublish" -> send(owner, "POST", "/api/planner/md/" + plannerId + "/unpublish", null);
            default -> throw new IllegalArgumentException(withdrawal);
        }
    }

    private void deletePlanner(User owner, UUID plannerId) throws Exception {
        send(owner, "DELETE", "/api/planner/md/" + plannerId, null);
    }

    private void deleteAccount(User owner) throws Exception {
        send(owner, "DELETE", "/api/user/me", null);
    }

    private void send(User user, String method, String path, String jsonBody) throws Exception {
        String csrf = csrfTokenService.mint();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header(CsrfDoubleSubmitFilter.CSRF_HEADER, csrf)
                .header("Cookie", CookieConstants.ACCESS_TOKEN + "=" + TestDataFactory.generateAccessToken(jwtTokenService, user)
                        + "; " + CookieConstants.CSRF + "=" + csrf + "; " + CookieConstants.DEVICE_ID + "=" + UUID.randomUUID())
                .method(method, jsonBody == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(jsonBody));
        HttpResponse<String> response = send(request);
        assertThat(response.statusCode())
                .as("%s %s answered %s", method, path, response.body())
                .isBetween(200, 299);
    }

    @Test
    void publish_WhenTheFilterRebuildRunsOnTheGtidPrimary_ReplicatesTheSameFilterRows() throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-filter-rows@example.com");
        UUID plannerId = TestDataFactory.createTestPlanner(plannerRepository, owner, true).getId();

        send(owner, "POST", "/api/planner/md/" + plannerId + "/unpublish", null);
        send(owner, "POST", "/api/planner/md/" + plannerId + "/publish", null);
        replicationControl.awaitCaughtUp();

        List<String> onPrimary = filterRows(primaryJdbcTemplate, plannerId);
        assertThat(onPrimary).as("the publish rebuilt the filter rows on the primary").isNotEmpty();
        assertThat(filterRows(replicaJdbcTemplate, plannerId))
                .as("a rebuild that stages rows in temporary tables replicates under GTID consistency")
                .isEqualTo(onPrimary);
    }

    private static List<String> filterRows(JdbcTemplate jdbc, UUID plannerId) {
        return jdbc.queryForList("""
                SELECT CONCAT(entity_type, ':', entity_id) AS row_key FROM planner_entity_filter
                WHERE planner_id = UUID_TO_BIN(?)
                UNION ALL
                SELECT CONCAT('KEYWORD:', keyword) FROM planner_keyword_filter WHERE planner_id = UUID_TO_BIN(?)
                ORDER BY row_key
                """, String.class, plannerId.toString(), plannerId.toString());
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

    private void assertNotFoundLikeACaughtUpReplica(HttpResponse<String> response, UUID plannerId) throws Exception {
        assertThat(response.statusCode())
                .as("the published read must be masked, not served: " + response.body())
                .isEqualTo(404);
        JsonNode problem = objectMapper.readTree(response.body());
        assertThat(problem.path("code").asString()).isEqualTo("PLANNER_NOT_FOUND");
        assertThat(problem.path("detail").asString()).isEqualTo("Planner not found with id: " + plannerId);
    }

    private HttpResponse<String> getPublished(UUID plannerId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/planner/md/published/" + plannerId)).GET().build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private List<String> batchPull(String method, User owner, List<UUID> ids) throws Exception {
        String accessToken = TestDataFactory.generateAccessToken(jwtTokenService, owner);
        String csrf = csrfTokenService.mint();
        String base = "http://localhost:" + port + "/api/planner/md/batch";
        HttpRequest.Builder request = "GET".equals(method)
                ? HttpRequest.newBuilder(URI.create(base + "?ids="
                        + ids.stream().map(UUID::toString).collect(Collectors.joining(",")))).GET()
                : HttpRequest.newBuilder(URI.create(base))
                        .header("Content-Type", "application/json")
                        .header(CsrfDoubleSubmitFilter.CSRF_HEADER, csrf)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(new PlannerBatchRequest(ids))));
        request.header("Cookie", CookieConstants.ACCESS_TOKEN + "=" + accessToken
                + "; " + CookieConstants.CSRF + "=" + csrf);

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            List<String> served = new ArrayList<>();
            for (JsonNode planner : objectMapper.readTree(response.body())) {
                served.add(planner.get("id").asString());
            }
            return served;
        }
    }

    @Test
    void publishedRead_WhenOwnerDeactivatedOnPausedReplica_Returns404LikeACaughtUpReplica() throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-deactivate@example.com");
        Planner first = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        Planner second = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        replicationControl.awaitCaughtUp();

        try {
            replicationControl.stopReplica();

            deleteAccount(owner);

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
    void splitReads_WhenOwnerDeactivatedOnPausedReplica_Return404AndRecordNoView() throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "replica-lag-split-owner@example.com");
        User viewer = TestDataFactory.createTestUser(userRepository, "replica-lag-split-viewer@example.com");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        UUID plannerId = planner.getId();
        replicationControl.awaitCaughtUp();

        try {
            replicationControl.stopReplica();

            deleteAccount(owner);

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
            UUID deviceId = UUID.randomUUID();
            String userAgent = "replica-lag-split-viewer";
            LocalDate before = LocalDate.now(ZoneOffset.UTC);
            assertNotFoundLikeACaughtUpReplica(send(HttpRequest.newBuilder(URI.create(base + "/viewcount"))
                    .header(CsrfDoubleSubmitFilter.CSRF_HEADER, csrf)
                    .header("Cookie", CookieConstants.CSRF + "=" + csrf + "; " + CookieConstants.DEVICE_ID + "=" + deviceId)
                    .header("User-Agent", userAgent)
                    .POST(HttpRequest.BodyPublishers.noBody())), plannerId);
            LocalDate after = LocalDate.now(ZoneOffset.UTC);

            assertThat(stringRedisTemplate.opsForHash().hasKey("views:buffer", plannerId.toString())).isFalse();
            String hash = ViewerHashUtil.hashForAnonymousUser("device:" + deviceId, userAgent, plannerId);
            assertThat(stringRedisTemplate.hasKey("views:seen:" + before + ":" + plannerId + ":" + hash)).isFalse();
            assertThat(stringRedisTemplate.hasKey("views:seen:" + after + ":" + plannerId + ":" + hash)).isFalse();
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void commentThread_WhenOwnerDeactivatedOnPausedReplica_Returns404LikeACaughtUpReplica() throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "comment-lag-owner@example.com");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        UUID plannerId = planner.getId();
        replicationControl.awaitCaughtUp();

        try {
            replicationControl.stopReplica();

            deleteAccount(owner);

            assertThat(replicaJdbcTemplate.queryForObject(
                    "SELECT deleted_at FROM users WHERE id = ?", Timestamp.class, owner.getId()))
                    .as("the paused replica must still hold the owner active")
                    .isNull();
            assertThat(stringRedisTemplate.hasKey(new FreshnessScope.Author(owner.getId()).key()))
                    .as("the deactivation must have raised the owner's Author marker")
                    .isTrue();

            assertNotFoundLikeACaughtUpReplica(getComments(plannerId), plannerId);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void commentThread_WhenOwnerDeactivatedOnCaughtUpReplica_Returns404() throws Exception {
        User owner = TestDataFactory.createTestUser(userRepository, "comment-caught-up-owner@example.com");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);

        deleteAccount(owner);
        replicationControl.awaitCaughtUp();

        assertNotFoundLikeACaughtUpReplica(getComments(planner.getId()), planner.getId());
    }

    private HttpResponse<String> getComments(UUID plannerId) throws Exception {
        return send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/planner/" + plannerId + "/comments")).GET());
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}

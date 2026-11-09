package org.danteplanner.backend.integration;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import jakarta.servlet.http.Cookie;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.PlannerBatchRequest;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessReadGuard;
import org.danteplanner.backend.shared.freshness.MarkerStore;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;
import org.danteplanner.backend.shared.util.ViewerHashUtil;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.UpsertRequests;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class FreshnessReadIT extends ReplicaRoutingHarnessSupport {

    private static final String PLANNERS_ROOT = "/api/planner/";
    private static final String PLANNERS = PLANNERS_ROOT + "md/";
    private static final String PUBLISHED = PLANNERS + "published/";
    private static final String CATALOG_PAGE = PLANNERS + "published?size=100";
    private static final String OWNER_LIST_PAGE = "/api/planner/md?size=100";
    private static final String BATCH = PLANNERS + "batch";
    private static final int OK = 200;
    private static final int NOT_FOUND = 404;
    private static final int UNAVAILABLE = 503;
    private static final int NO_CONTENT = 204;
    private static final int READS_PER_VIEWER = 3;
    private static final long REPLICATION_LATENCY_UNDER_THE_WAIT_BOUND_MILLIS = 120;
    private static final int LAG_UNDER_BOUND_ROUNDS = 20;
    private static final String SEOUL_REPLICA = "seoul-replica";
    private static final String NEW_EPITHET = "NAIVE";
    private static final String VIEW_BUFFER_KEY = "views:buffer";
    private static final String VIEW_SEEN_KEY_PREFIX = "views:seen:";
    private static final String DEVICE_IDENTITY_PREFIX = "device:";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserAccountLifecycleService lifecycleService;

    @Autowired
    private GtidWriteCapture writeCapture;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private MarkerStore markerStore;

    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;

    @Autowired
    @Qualifier("replicaJdbcTemplate")
    private JdbcTemplate replicaJdbcTemplate;

    @Autowired
    @Qualifier("stringRedisTemplate")
    private StringRedisTemplate redisTemplate;

    @Test
    void publishedDetail_WhenTheReplicaStillHoldsADeletedPlanner_NeverServesIt() throws Exception {
        Cookie[] owner = session("freshness-read-delete");
        UUID plannerId = published(owner, "doomed");
        replicationControl.awaitCaughtUp();
        try {
            replicationControl.stopReplica();
            perform(delete(PLANNERS + plannerId).cookie(owner));
            assertThat(replicaJdbcTemplate.queryForObject(
                    "SELECT deleted_at IS NULL FROM planner_content WHERE planner_id = UUID_TO_BIN(?)",
                    Boolean.class, plannerId.toString())).isTrue();

            assertThat(mockMvc.perform(get(PUBLISHED + plannerId)).andReturn().getResponse().getStatus())
                    .isIn(NOT_FOUND, UNAVAILABLE);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void publishedReads_WhenTheReplicaLagsATitleEdit_ServeTheNewTitle() throws Exception {
        Cookie[] owner = session("freshness-read-title");
        UUID plannerId = published(owner, "before-edit");
        replicationControl.awaitCaughtUp();
        try {
            replicationControl.stopReplica();
            perform(put(PLANNERS + plannerId)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(UpsertRequests.draft()
                            .id(plannerId.toString())
                            .title("after-edit")
                            .status(null)
                            .syncVersion(syncVersion(plannerId))
                            .build()))
                    .cookie(owner));

            assertThat(json(get(PUBLISHED + plannerId)).path("title").asText()).isEqualTo("after-edit");
            assertThat(catalogTitles(plannerId)).containsExactly("after-edit");
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void ownerList_WhenTheReplicaLagsACreate_ListsTheNewPlanner() throws Exception {
        Cookie[] owner = session("freshness-read-create");
        replicationControl.awaitCaughtUp();
        try {
            replicationControl.stopReplica();
            UUID plannerId = draft(owner, "created-while-lagging");

            List<String> listed = new ArrayList<>();
            json(get(OWNER_LIST_PAGE).cookie(owner)).path("content")
                    .forEach(row -> listed.add(row.path("id").asText()));
            assertThat(listed).contains(plannerId.toString());
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void ownerById_WhenTheReplicaLagsADelete_NeverServesThePlanner() throws Exception {
        Cookie[] owner = session("freshness-read-by-id-delete");
        UUID plannerId = draft(owner, "deleted-while-lagging");
        replicationControl.awaitCaughtUp();
        try {
            replicationControl.stopReplica();
            perform(delete(PLANNERS + plannerId).cookie(owner));
            assertThat(replicaJdbcTemplate.queryForObject(
                    "SELECT deleted_at IS NULL FROM planner_content WHERE planner_id = UUID_TO_BIN(?)",
                    Boolean.class, plannerId.toString())).isTrue();

            assertThat(status(get(PLANNERS + plannerId).cookie(owner))).isIn(NOT_FOUND, UNAVAILABLE);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void ownerById_WhenTheReplicaLagsACreate_ServesTheNewPlanner() throws Exception {
        Cookie[] owner = session("freshness-read-by-id-create");
        replicationControl.awaitCaughtUp();
        try {
            replicationControl.stopReplica();
            UUID plannerId = draft(owner, "created-while-lagging");

            MvcResult result = mockMvc.perform(get(PLANNERS + plannerId).cookie(owner)).andReturn();
            assertThat(result.getResponse().getStatus()).isIn(OK, UNAVAILABLE);
            if (result.getResponse().getStatus() == OK) {
                assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asText())
                        .isEqualTo(plannerId.toString());
            }
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void ownerBatch_WhenTheReplicaLagsACreate_ReturnsTheNewPlanner() throws Exception {
        Cookie[] owner = session("freshness-read-batch");
        UUID absent = UUID.randomUUID();
        replicationControl.awaitCaughtUp();
        try {
            replicationControl.stopReplica();
            UUID plannerId = draft(owner, "batched-while-lagging");

            assertThat(ids(json(get(BATCH).param("ids", plannerId + "," + absent).cookie(owner))))
                    .containsExactly(plannerId.toString());
            assertThat(ids(json(post(BATCH)
                    .with(withCsrf())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new PlannerBatchRequest(List.of(plannerId, absent))))
                    .cookie(owner))))
                    .containsExactly(plannerId.toString());
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void publishedDetail_WhenTheReplicaLagsAnAuthorReactivation_NeverAnswers404() throws Exception {
        User author = TestDataFactory.createTestUser(userRepository, "freshness-read-reactivate-" + UUID.randomUUID()
                + "@example.com");
        Cookie[] owner = sessionOf(author);
        UUID plannerId = published(owner, "reactivated");
        perform(delete("/api/user/me").cookie(owner));
        replicationControl.awaitCaughtUp();
        try {
            replicationControl.stopReplica();
            reactivateInARequestWindow(author.getId());
            assertThat(replicaJdbcTemplate.queryForObject(
                    "SELECT deleted_at IS NOT NULL FROM users WHERE id = ?", Boolean.class, author.getId())).isTrue();

            assertThat(mockMvc.perform(get(PUBLISHED + plannerId)).andReturn().getResponse().getStatus())
                    .isIn(OK, UNAVAILABLE);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void commentThread_WhenTheReplicaLagsAnAuthorReactivation_NeverAnswers404() throws Exception {
        User author = TestDataFactory.createTestUser(userRepository, "freshness-thread-reactivate-" + UUID.randomUUID()
                + "@example.com");
        Cookie[] owner = sessionOf(author);
        UUID plannerId = published(owner, "thread-reactivated");
        perform(delete("/api/user/me").cookie(owner));
        replicationControl.awaitCaughtUp();
        try {
            replicationControl.stopReplica();
            reactivateInARequestWindow(author.getId());
            assertThat(replicaJdbcTemplate.queryForObject(
                    "SELECT deleted_at IS NOT NULL FROM users WHERE id = ?", Boolean.class, author.getId())).isTrue();

            assertThat(status(get(PLANNERS_ROOT + plannerId + "/comments"))).isIn(OK, UNAVAILABLE);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void commentThread_WhenHiddenOrAbsent_AnswersTheSameNotFoundBody() throws Exception {
        Cookie[] owner = session("freshness-thread-hidden");
        UUID plannerId = published(owner, "thread-hidden-then-absent");
        perform(post(PLANNERS + plannerId + "/unpublish").cookie(owner));
        MvcResult hidden = mockMvc.perform(get(PLANNERS_ROOT + plannerId + "/comments")).andReturn();

        perform(delete(PLANNERS + plannerId).cookie(owner));
        MvcResult absent = mockMvc.perform(get(PLANNERS_ROOT + plannerId + "/comments")).andReturn();

        assertThat(hidden.getResponse().getStatus()).isEqualTo(NOT_FOUND);
        assertThat(absent.getResponse().getStatus()).isEqualTo(NOT_FOUND);
        assertThat(hidden.getResponse().getContentAsByteArray()).isEqualTo(absent.getResponse().getContentAsByteArray());
    }

    @Test
    void commentThread_WhenTheReplicaLagsAnUnpublish_NeverShowsTheThread() throws Exception {
        Cookie[] owner = session("freshness-thread-unpublish");
        UUID plannerId = published(owner, "thread-unpublished-while-lagging");
        replicationControl.awaitCaughtUp();
        try {
            replicationControl.stopReplica();
            perform(post(PLANNERS + plannerId + "/unpublish").cookie(owner));

            assertThat(status(get(PLANNERS_ROOT + plannerId + "/comments"))).isIn(NOT_FOUND, UNAVAILABLE);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void commentThread_WhenTheReplicaLagsAPublish_NeverAnswers404() throws Exception {
        Cookie[] owner = session("freshness-thread-publish");
        UUID plannerId = draft(owner, "thread-published-while-lagging");
        replicationControl.awaitCaughtUp();
        try {
            replicationControl.stopReplica();
            perform(post(PLANNERS + plannerId + "/publish").cookie(owner));

            assertThat(status(get(PLANNERS_ROOT + plannerId + "/comments"))).isIn(OK, UNAVAILABLE);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void publicList_WhenTheReplicaLagsAnUnpublish_OmitsThePlanner() throws Exception {
        Cookie[] owner = session("freshness-read-unpublish");
        UUID plannerId = published(owner, "unpublished-while-lagging");
        replicationControl.awaitCaughtUp();
        try {
            replicationControl.stopReplica();
            perform(post(PLANNERS + plannerId + "/unpublish").cookie(owner));

            assertThat(catalogTitles(plannerId)).isEmpty();
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void publishedReads_WhenTheReplicaLagsAnEpithetChange_ServeTheNewEpithet() throws Exception {
        Cookie[] owner = session("freshness-read-epithet");
        UUID plannerId = published(owner, "epithet-owner");
        replicationControl.awaitCaughtUp();
        try {
            replicationControl.stopReplica();
            perform(put("/api/user/me/username-epithet")
                    .contentType(APPLICATION_JSON)
                    .content("{\"epithet\":\"" + NEW_EPITHET + "\"}")
                    .cookie(owner));

            assertThat(json(get(PUBLISHED + plannerId)).path("authorUsernameEpithet").asText())
                    .isEqualTo(NEW_EPITHET);
            assertThat(catalogEpithets(plannerId)).containsExactly(NEW_EPITHET);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void splitReads_WhenTheReplicaLagsAPublish_NeverAnswer404() throws Exception {
        Cookie[] owner = session("freshness-read-publish");
        Cookie[] reader = session("freshness-read-publish-reader");
        UUID plannerId = draft(owner, "published-while-lagging");
        replicationControl.awaitCaughtUp();
        try {
            replicationControl.stopReplica();
            perform(post(PLANNERS + plannerId + "/publish").cookie(owner));

            assertThat(status(get(PUBLISHED + plannerId + "/stats"))).isIn(OK, UNAVAILABLE);
            assertThat(status(get(PUBLISHED + plannerId + "/flags"))).isIn(OK, UNAVAILABLE);
            assertThat(status(get(PUBLISHED + plannerId + "/flags").cookie(reader))).isIn(OK, UNAVAILABLE);
            assertThat(status(post(PUBLISHED + plannerId + "/viewcount").with(withCsrf())))
                    .isIn(NO_CONTENT, UNAVAILABLE);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void publishedDetail_WhenReplicationLagsUnderTheWaitBound_ServesTheWriteBeforeItsResponse() throws Exception {
        Cookie[] owner = session("freshness-read-lag-under-bound");
        UUID plannerId = draft(owner, "lag-under-bound");
        replicationControl.awaitCaughtUp();
        List<String> stale = new ArrayList<>();
        int publishWaitsApplied = 0;
        int unpublishWaitsApplied = 0;
        int publishAnswered = 0;
        int unpublishAnswered = 0;
        try {
            toxiproxyControl.applyReplicationLatency(REPLICATION_LATENCY_UNDER_THE_WAIT_BOUND_MILLIS);
            for (int round = 0; round < LAG_UNDER_BOUND_ROUNDS; round++) {
                perform(post(PLANNERS + plannerId + "/publish").cookie(owner));
                double beforePublishRead = seoulWaitsApplied();
                int afterPublish = status(get(PUBLISHED + plannerId));
                publishWaitsApplied += seoulWaitsApplied() > beforePublishRead ? 1 : 0;
                publishAnswered += afterPublish == OK ? 1 : 0;
                if (afterPublish != OK && afterPublish != UNAVAILABLE) {
                    stale.add("round " + round + " publish → " + afterPublish);
                }
                perform(post(PLANNERS + plannerId + "/unpublish").cookie(owner));
                double beforeUnpublishRead = seoulWaitsApplied();
                int afterUnpublish = status(get(PUBLISHED + plannerId));
                unpublishWaitsApplied += seoulWaitsApplied() > beforeUnpublishRead ? 1 : 0;
                unpublishAnswered += afterUnpublish == NOT_FOUND ? 1 : 0;
                if (afterUnpublish != NOT_FOUND && afterUnpublish != UNAVAILABLE) {
                    stale.add("round " + round + " unpublish → " + afterUnpublish);
                }
            }
        } finally {
            toxiproxyControl.removeReplicationLatency();
            replicationControl.awaitCaughtUp();
        }

        assertThat(publishWaitsApplied).as("reads after a publish whose Seoul wait applied").isPositive();
        assertThat(unpublishWaitsApplied).as("reads after an unpublish whose Seoul wait applied").isPositive();
        assertThat(publishAnswered).as("reads after a publish answered 200").isPositive();
        assertThat(unpublishAnswered).as("reads after an unpublish answered 404").isPositive();
        assertThat(stale).isEmpty();
    }

    @Test
    void publishedDetail_WhenHiddenOrAbsent_AnswersTheSameNotFoundBody() throws Exception {
        Cookie[] owner = session("freshness-read-hidden");
        UUID plannerId = published(owner, "hidden-then-absent");
        perform(post(PLANNERS + plannerId + "/unpublish").cookie(owner));
        MvcResult hidden = mockMvc.perform(get(PUBLISHED + plannerId)).andReturn();

        perform(delete(PLANNERS + plannerId).cookie(owner));
        MvcResult absent = mockMvc.perform(get(PUBLISHED + plannerId)).andReturn();

        assertThat(hidden.getResponse().getStatus()).isEqualTo(NOT_FOUND);
        assertThat(absent.getResponse().getStatus()).isEqualTo(NOT_FOUND);
        assertThat(hidden.getResponse().getContentAsByteArray()).isEqualTo(absent.getResponse().getContentAsByteArray());
    }

    @Test
    void publishedDetail_WhenReadRepeatedly_RecordsNoView() throws Exception {
        Cookie[] owner = session("freshness-read-views");
        User reader = TestDataFactory.createTestUser(userRepository, "freshness-read-views-reader-" + UUID.randomUUID()
                + "@example.com");
        UUID device = UUID.randomUUID();
        UUID plannerId = published(owner, "read-not-viewed");
        LocalDate before = LocalDate.now(ZoneOffset.UTC);

        for (int read = 0; read < READS_PER_VIEWER; read++) {
            json(get(PUBLISHED + plannerId).cookie(AuthCookies.deviceId(device)));
            json(get(PUBLISHED + plannerId).cookie(sessionOf(reader)));
        }
        LocalDate after = LocalDate.now(ZoneOffset.UTC);

        assertThat(primaryJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM planner_views WHERE planner_id = UUID_TO_BIN(?)",
                Long.class, plannerId.toString())).isZero();
        List<String> viewerHashes = List.of(
                ViewerHashUtil.hashForAnonymousUser(DEVICE_IDENTITY_PREFIX + device, null, plannerId),
                ViewerHashUtil.hashForAuthenticatedUser(reader.getId(), plannerId));
        for (String hash : viewerHashes) {
            assertThat(redisTemplate.hasKey(seenKey(before, plannerId, hash))).isFalse();
            assertThat(redisTemplate.hasKey(seenKey(after, plannerId, hash))).isFalse();
        }
        assertThat(redisTemplate.opsForHash().hasKey(VIEW_BUFFER_KEY, plannerId.toString())).isFalse();
    }

    private void reactivateInARequestWindow(Long userId) {
        writeCapture.begin();
        FreshnessDeclarations.begin();
        try {
            lifecycleService.reactivateAccount(userId);
            String gtid = writeCapture.takeCapturedGtid().orElseThrow();
            markerStore.raise(FreshnessDeclarations.declared(), gtid);
        } finally {
            writeCapture.clear();
            FreshnessDeclarations.clear();
        }
    }

    private List<String> catalogTitles(UUID plannerId) throws Exception {
        List<String> titles = new ArrayList<>();
        json(get(CATALOG_PAGE)).path("content").forEach(row -> {
            if (row.path("id").asText().equals(plannerId.toString())) {
                titles.add(row.path("title").asText());
            }
        });
        return titles;
    }

    private List<String> catalogEpithets(UUID plannerId) throws Exception {
        List<String> epithets = new ArrayList<>();
        json(get(CATALOG_PAGE)).path("content").forEach(row -> {
            if (row.path("id").asText().equals(plannerId.toString())) {
                epithets.add(row.path("authorUsernameEpithet").asText());
            }
        });
        return epithets;
    }

    private static List<String> ids(JsonNode planners) {
        List<String> ids = new ArrayList<>();
        planners.forEach(planner -> ids.add(planner.path("id").asText()));
        return ids;
    }

    private static String seenKey(LocalDate viewDate, UUID plannerId, String viewerHash) {
        return VIEW_SEEN_KEY_PREFIX + viewDate + ":" + plannerId + ":" + viewerHash;
    }

    private double seoulWaitsApplied() {
        Timer waits = meterRegistry.find(FreshnessReadGuard.READ_WAIT)
                .tag(FreshnessReadGuard.SOURCE_TAG, SEOUL_REPLICA)
                .tag(FreshnessReadGuard.OUTCOME_TAG, FreshnessReadGuard.APPLIED)
                .timer();
        return waits == null ? 0 : waits.count();
    }

    private int status(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    private Long syncVersion(UUID plannerId) {
        return primaryJdbcTemplate.queryForObject(
                "SELECT sync_version FROM planner_content WHERE planner_id = UUID_TO_BIN(?)",
                Long.class, plannerId.toString());
    }

    private JsonNode json(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus())
                .as("%s answered %s", result.getRequest().getRequestURI(), result.getResponse().getContentAsString())
                .isEqualTo(OK);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void perform(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request.with(withCsrf())).andReturn();
        assertThat(result.getResponse().getStatus())
                .as("%s %s answered %s", result.getRequest().getMethod(), result.getRequest().getRequestURI(),
                        result.getResponse().getContentAsString())
                .isBetween(200, 299);
    }

    private Cookie[] session(String prefix) {
        return sessionOf(TestDataFactory.createTestUser(userRepository, prefix + "-" + UUID.randomUUID()
                + "@example.com"));
    }

    private Cookie[] sessionOf(User user) {
        return new Cookie[] {
            AuthCookies.accessToken(TestDataFactory.generateAccessToken(jwtTokenService, user)),
            AuthCookies.freshDeviceId()};
    }

    private UUID draft(Cookie[] owner, String title) throws Exception {
        UUID plannerId = UUID.randomUUID();
        perform(put(PLANNERS + plannerId)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        UpsertRequests.draft().id(plannerId.toString()).title(title).build()))
                .cookie(owner));
        return plannerId;
    }

    private UUID published(Cookie[] owner, String title) throws Exception {
        UUID plannerId = draft(owner, title);
        perform(post(PLANNERS + plannerId + "/publish").cookie(owner));
        return plannerId;
    }
}

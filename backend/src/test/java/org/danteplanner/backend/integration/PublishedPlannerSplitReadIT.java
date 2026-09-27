package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.planner.repository.ViewFlushBatchRepository;
import org.danteplanner.backend.planner.scheduler.ViewCountFlushJob;
import org.danteplanner.backend.planner.service.PlannerSubscriptionService;
import org.danteplanner.backend.planner.service.RedisViewRecorder;
import org.danteplanner.backend.shared.util.ViewerHashUtil;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.Spliterators;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PublishedPlannerSplitReadIT extends SharedMySqlContainerSupport {

    private static final String BUFFER_KEY = "views:buffer";
    private static final String BATCH_KEY_PREFIX = "views:batch:";
    private static final String BATCHES_KEY = "views:batches";
    private static final String BATCH_FAILED_COUNTER = "views.flush.batch.failed";
    private static final int VIEW_RECORD_CAPACITY = 30;
    private static final long PUBLIC_READ_CAPACITY = 120;
    private static final long PLANNER_STATS_CAPACITY = 120;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerStatsRepository plannerStatsRepository;

    @Autowired
    private PlannerVoteRepository plannerVoteRepository;

    @Autowired
    private PlannerSubscriptionService subscriptionService;

    @Autowired
    private ViewFlushBatchRepository viewFlushBatchRepository;

    @Autowired
    private ViewCountFlushJob viewCountFlushJob;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private RedisViewRecorder redisViewRecorder;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ProxyManager<byte[]> rateLimitProxyManager;

    private User owner;
    private User viewer;
    private Planner published;

    @BeforeEach
    void setUp() {
        owner = TestDataFactory.createTestUser(userRepository, "split-read-owner@example.com");
        viewer = TestDataFactory.createTestUser(userRepository, "split-read-viewer@example.com");
        published = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
    }

    private Cookie authOf(User user) {
        return AuthCookies.accessToken(TestDataFactory.generateAccessToken(jwtTokenService, user));
    }

    private int viewCountOf(UUID plannerId) {
        return plannerStatsRepository.findById(plannerId).map(PlannerStats::getViewCount).orElse(0);
    }

    private Set<String> seenKeysOf(UUID plannerId) {
        return redisTemplate.keys("views:seen:*:" + plannerId + ":*");
    }

    private static Set<String> fieldNames(JsonNode node) {
        return Set.copyOf(StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(node.fieldNames(), 0), false).toList());
    }

    @Test
    @DisplayName("Scenario 3: two viewcount posts by one user on one UTC day count once and leave one expiring seen key")
    void viewCount_WhenSameUserPostsTwiceInOneDay_BuffersOneViewUnderOneExpiringSeenKey() throws Exception {
        UUID plannerId = published.getId();
        LocalDate before = LocalDate.now(ZoneOffset.UTC);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/planner/md/published/{id}/viewcount", plannerId).with(withCsrf())
                            .cookie(authOf(viewer)))
                    .andExpect(status().isNoContent());
        }
        LocalDate after = LocalDate.now(ZoneOffset.UTC);

        assertThat(redisTemplate.opsForHash().get(BUFFER_KEY, plannerId.toString())).isEqualTo("1");

        String hash = ViewerHashUtil.hashForAuthenticatedUser(viewer.getId(), plannerId);
        Set<String> seen = seenKeysOf(plannerId);
        assertThat(seen).hasSize(1);
        String key = seen.iterator().next();
        assertThat(key).isIn(
                "views:seen:" + before + ":" + plannerId + ":" + hash,
                "views:seen:" + after + ":" + plannerId + ":" + hash);
        assertThat(redisTemplate.getExpire(key)).isGreaterThanOrEqualTo(86_400L - 5L);
    }

    @Test
    @DisplayName("A buffered view reaches the stats counter on flush and leaves neither the buffer entry nor a batch key")
    void flush_WhenBufferHoldsAView_AppliesItAndClearsTheBuffer() throws Exception {
        UUID plannerId = published.getId();
        int before = viewCountOf(plannerId);

        mockMvc.perform(post("/api/planner/md/published/{id}/viewcount", plannerId).with(withCsrf())
                        .cookie(authOf(viewer)))
                .andExpect(status().isNoContent());
        viewCountFlushJob.flush();

        assertThat(viewCountOf(plannerId)).isEqualTo(before + 1);
        assertThat(redisTemplate.opsForHash().hasKey(BUFFER_KEY, plannerId.toString())).isFalse();
    }

    @Test
    @DisplayName("Scenario 4a: a leftover batch whose id is recorded is deleted without being applied again")
    void flush_WhenLeftoverBatchIsAlreadyRecorded_DeletesItWithoutReapplying() {
        UUID plannerId = published.getId();
        UUID batchId = UUID.randomUUID();
        String batchKey = BATCH_KEY_PREFIX + batchId;
        transactionTemplate.executeWithoutResult(status -> viewFlushBatchRepository.insertBatch(batchId));
        redisTemplate.opsForHash().put(batchKey, plannerId.toString(), "5");
        redisTemplate.opsForSet().add(BATCHES_KEY, batchId.toString());
        int before = viewCountOf(plannerId);

        viewCountFlushJob.flush();

        assertThat(redisTemplate.hasKey(batchKey)).isFalse();
        assertThat(viewCountOf(plannerId)).isEqualTo(before);
    }

    @Test
    @DisplayName("Scenario 4b: a leftover batch whose id is not recorded is applied, recorded, then deleted")
    void flush_WhenLeftoverBatchIsNotRecorded_AppliesRecordsAndDeletesIt() {
        UUID plannerId = published.getId();
        UUID batchId = UUID.randomUUID();
        String batchKey = BATCH_KEY_PREFIX + batchId;
        redisTemplate.opsForHash().put(batchKey, plannerId.toString(), "5");
        redisTemplate.opsForSet().add(BATCHES_KEY, batchId.toString());
        int before = viewCountOf(plannerId);

        viewCountFlushJob.flush();

        assertThat(viewCountOf(plannerId)).isEqualTo(before + 5);
        assertThat(viewFlushBatchRepository.existsById(batchId)).isTrue();
        assertThat(redisTemplate.hasKey(batchKey)).isFalse();
    }

    @Test
    @DisplayName("Scenario 9: stats carry exactly the detail read's counts and are never cached")
    void stats_WhenPublished_ReturnsTheCountsWithNoStore() throws Exception {
        UUID plannerId = published.getId();
        transactionTemplate.executeWithoutResult(status -> {
            plannerStatsRepository.incrementViewCountBy(plannerId, 4);
            plannerStatsRepository.incrementUpvotes(plannerId);
            plannerStatsRepository.incrementCommentCount(plannerId);
        });
        PlannerStats stats = plannerStatsRepository.findById(plannerId).orElseThrow();

        MvcResult result = mockMvc.perform(get("/api/planner/md/published/{id}/stats", plannerId))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.viewCount").value(stats.getViewCount()))
                .andExpect(jsonPath("$.upvotes").value(stats.getUpvotes()))
                .andExpect(jsonPath("$.commentCount").value(stats.getCommentCount()))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(fieldNames(body)).containsExactlyInAnyOrder("viewCount", "upvotes", "commentCount");
    }

    @Test
    @DisplayName("Flags for a non-owner who upvoted and subscribed are true, true and false, never cached")
    void flags_WhenNonOwnerUpvotedAndSubscribed_ReturnsTrueTrueFalseWithNoStore() throws Exception {
        UUID plannerId = published.getId();
        plannerVoteRepository.save(new PlannerVote(viewer.getId(), plannerId, VoteType.UP));
        subscriptionService.createSubscription(viewer.getId(), plannerId);

        MvcResult result = mockMvc.perform(get("/api/planner/md/published/{id}/flags", plannerId)
                        .cookie(authOf(viewer)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.hasUpvoted").value(true))
                .andExpect(jsonPath("$.isSubscribed").value(true))
                .andExpect(jsonPath("$.ownerNotificationsEnabled").value(false))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(fieldNames(body))
                .containsExactlyInAnyOrder("hasUpvoted", "isSubscribed", "ownerNotificationsEnabled");
    }

    @Test
    @DisplayName("Flags for the owner who upvoted and subscribed carry the owner's notification setting")
    void flags_WhenOwnerUpvotedAndSubscribed_CarriesTheOwnerNotificationSetting() throws Exception {
        UUID plannerId = published.getId();
        plannerVoteRepository.save(new PlannerVote(owner.getId(), plannerId, VoteType.UP));
        subscriptionService.createSubscription(owner.getId(), plannerId);

        mockMvc.perform(get("/api/planner/md/published/{id}/flags", plannerId).cookie(authOf(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasUpvoted").value(true))
                .andExpect(jsonPath("$.isSubscribed").value(true))
                .andExpect(jsonPath("$.ownerNotificationsEnabled").value(published.isOwnerNotificationsEnabled()));
    }

    @Test
    @DisplayName("Flags for an anonymous caller are all false")
    void flags_WhenAnonymous_ReturnsAllFalse() throws Exception {
        mockMvc.perform(get("/api/planner/md/published/{id}/flags", published.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasUpvoted").value(false))
                .andExpect(jsonPath("$.isSubscribed").value(false))
                .andExpect(jsonPath("$.ownerNotificationsEnabled").value(false));
    }

    @Test
    @DisplayName("Stats, flags and viewcount on an unpublished planner answer the detail read's 404")
    void splitEndpoints_WhenPlannerUnpublished_AnswerTheDetailReadNotFound() throws Exception {
        UUID plannerId = TestDataFactory.createTestPlanner(plannerRepository, owner, false).getId();
        String detail = "Planner not found with id: " + plannerId;

        mockMvc.perform(get("/api/planner/md/published/{id}", plannerId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value(detail));

        for (String path : List.of("stats", "flags")) {
            mockMvc.perform(get("/api/planner/md/published/{id}/" + path, plannerId).cookie(authOf(viewer)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"))
                    .andExpect(jsonPath("$.detail").value(detail));
        }
        mockMvc.perform(post("/api/planner/md/published/{id}/viewcount", plannerId).with(withCsrf())
                        .cookie(authOf(viewer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value(detail));

        assertThat(redisTemplate.opsForHash().hasKey(BUFFER_KEY, plannerId.toString())).isFalse();
        assertThat(seenKeysOf(plannerId)).isEmpty();
    }

    @Test
    @DisplayName("An anonymous viewcount post is accepted without a login")
    void viewCount_WhenAnonymous_Returns204AndBuffersTheView() throws Exception {
        UUID plannerId = published.getId();

        mockMvc.perform(post("/api/planner/md/published/{id}/viewcount", plannerId).with(withCsrf())
                        .cookie(AuthCookies.freshDeviceId()))
                .andExpect(status().isNoContent());

        assertThat(redisTemplate.opsForHash().get(BUFFER_KEY, plannerId.toString())).isEqualTo("1");
    }

    @Test
    @DisplayName("A leftover batch key that was never registered in the batch set is not discovered or replayed")
    void flush_WhenBatchKeyIsNotRegistered_LeavesItUnreplayed() {
        UUID plannerId = published.getId();
        UUID batchId = UUID.randomUUID();
        String batchKey = BATCH_KEY_PREFIX + batchId;
        redisTemplate.opsForHash().put(batchKey, plannerId.toString(), "5");
        int before = viewCountOf(plannerId);
        try {
            assertThat(redisViewRecorder.leftoverBatchIds())
                    .extracting(Object::toString)
                    .doesNotContain(batchId.toString());

            viewCountFlushJob.flush();

            assertThat(viewCountOf(plannerId)).isEqualTo(before);
            assertThat(redisTemplate.hasKey(batchKey)).isTrue();
        } finally {
            redisTemplate.delete(batchKey);
        }
    }

    @Test
    @DisplayName("A registered batch that cannot be applied is counted and skipped, and the live buffer is still flushed")
    void flush_WhenARegisteredBatchCannotBeApplied_CountsItAndStillFlushesTheBuffer() throws Exception {
        UUID plannerId = published.getId();
        String poison = "not-a-batch-id-" + UUID.randomUUID();
        redisTemplate.opsForSet().add(BATCHES_KEY, poison);
        try {
            mockMvc.perform(post("/api/planner/md/published/{id}/viewcount", plannerId).with(withCsrf())
                            .cookie(authOf(viewer)))
                    .andExpect(status().isNoContent());
            int before = viewCountOf(plannerId);
            double failedBefore = batchFailedCount();

            viewCountFlushJob.flush();

            assertThat(batchFailedCount() - failedBefore).isEqualTo(1.0);
            assertThat(viewCountOf(plannerId)).isEqualTo(before + 1);
            assertThat(redisTemplate.opsForHash().hasKey(BUFFER_KEY, plannerId.toString())).isFalse();
        } finally {
            redisTemplate.opsForSet().remove(BATCHES_KEY, poison);
        }
    }

    @Test
    @DisplayName("Stats, flags and viewcount on a planner whose content is soft-deleted answer the detail read's 404")
    void splitEndpoints_WhenContentSoftDeleted_AnswerTheDetailReadNotFound() throws Exception {
        published.softDelete();
        plannerRepository.save(published);

        assertHiddenFromEveryReadPath(published.getId());
    }

    @Test
    @DisplayName("Stats, flags and viewcount on a planner a moderator took down answer the detail read's 404")
    void splitEndpoints_WhenTakenDownByModerator_AnswerTheDetailReadNotFound() throws Exception {
        User moderator = TestDataFactory.createModerator(userRepository, "split-read-moderator@example.com");
        mockMvc.perform(post("/api/moderation/planner/{id}/takedown", published.getId()).with(withCsrf())
                        .cookie(authOf(moderator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Content violates community guidelines\"}"))
                .andExpect(status().isOk());

        assertHiddenFromEveryReadPath(published.getId());
    }

    @Test
    @DisplayName("Stats, flags and viewcount on a planner whose owner is deactivated answer the detail read's 404")
    void splitEndpoints_WhenOwnerDeactivated_AnswerTheDetailReadNotFound() throws Exception {
        owner.softDelete(Instant.now().plusSeconds(86_400L * 30));
        userRepository.save(owner);

        assertHiddenFromEveryReadPath(published.getId());
    }

    private void assertHiddenFromEveryReadPath(UUID plannerId) throws Exception {
        String detail = "Planner not found with id: " + plannerId;

        mockMvc.perform(get("/api/planner/md/published/{id}", plannerId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value(detail));
        for (String path : List.of("stats", "flags")) {
            mockMvc.perform(get("/api/planner/md/published/{id}/" + path, plannerId).cookie(authOf(viewer)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"))
                    .andExpect(jsonPath("$.detail").value(detail));
        }
        mockMvc.perform(post("/api/planner/md/published/{id}/viewcount", plannerId).with(withCsrf())
                        .cookie(authOf(viewer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value(detail));

        assertThat(redisTemplate.opsForHash().hasKey(BUFFER_KEY, plannerId.toString())).isFalse();
        assertThat(seenKeysOf(plannerId)).isEmpty();
    }

    private double batchFailedCount() {
        Counter counter = meterRegistry.find(BATCH_FAILED_COUNTER).counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    @DisplayName("Exhausting a client's view-record budget leaves its public-read and planner-stats budgets whole")
    void viewCount_WhenClientExhaustsViewRecordBudget_LeavesPublicReadAndPlannerStatsBudgetsWhole() throws Exception {
        UUID deviceId = UUID.randomUUID();
        Cookie device = AuthCookies.deviceId(deviceId);
        for (int i = 0; i < VIEW_RECORD_CAPACITY; i++) {
            mockMvc.perform(post("/api/planner/md/published/{id}/viewcount", published.getId()).with(withCsrf())
                            .cookie(device))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(post("/api/planner/md/published/{id}/viewcount", published.getId()).with(withCsrf())
                        .cookie(device))
                .andExpect(status().isTooManyRequests());

        assertThat(availableTokens("device:" + deviceId + ":public-read", PUBLIC_READ_CAPACITY))
                .isEqualTo(PUBLIC_READ_CAPACITY);
        assertThat(availableTokens("device:" + deviceId + ":planner-stats", PLANNER_STATS_CAPACITY))
                .isEqualTo(PLANNER_STATS_CAPACITY);
    }

    private long availableTokens(String key, long capacity) {
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder().capacity(capacity)
                        .refillGreedy(capacity, Duration.ofSeconds(60)).build())
                .build();
        return rateLimitProxyManager.builder()
                .build(key.getBytes(StandardCharsets.UTF_8), () -> configuration)
                .getAvailableTokens();
    }
}

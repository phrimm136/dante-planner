package org.danteplanner.backend.integration;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.servlet.http.Cookie;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.freshness.MarkerStore;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.UpsertRequests;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class HarnessMarkerReplicationIT extends ReplicaRoutingHarnessSupport {

    private static final String PLANNERS = "/api/planner/md/";
    private static final String REPLICA_ROLE = "slave";
    private static final Duration RESPONSE_MARGIN = Duration.ofSeconds(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private MarkerStore.Settings markerSettings;

    @Autowired
    @Qualifier("stringRedisTemplate")
    private StringRedisTemplate primaryRedis;

    @Autowired
    @Qualifier("authLocalStringRedisTemplate")
    private StringRedisTemplate localRedis;

    @Test
    void plannerCreate_WhenTheRedisReplicaAcknowledges_AnswersWithTheMarkerAlreadyOnTheLocalReplica()
            throws Exception {
        assertThat(info(localRedis).getProperty("role"))
                .as("the Seoul context's auth-local store is the Redis replica")
                .isEqualTo(REPLICA_ROLE);

        assertCreateReachesTheReplicaWithoutATimeout();
    }

    @Test
    void plannerCreate_WhenTheRedisReplicaStopsAcknowledging_AnswersAfterTheBoundAndCountsOneTimeoutThenRecovers()
            throws Exception {
        Cookie[] owner = owner();
        double timeoutsBefore = waitTimeouts();
        long elapsedMillis;
        holdAuthRedisReplicaAcks();
        try {
            long start = System.nanoTime();
            create(owner, UUID.randomUUID());
            elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        } finally {
            releaseAuthRedisReplicaAcks();
        }

        assertThat(elapsedMillis)
                .isGreaterThanOrEqualTo(markerSettings.waitBound().toMillis())
                .isLessThan(markerSettings.waitBound().plus(RESPONSE_MARGIN).toMillis());
        assertThat(waitTimeouts() - timeoutsBefore).isEqualTo(1.0);

        awaitAuthRedisReplicaLinked();
        assertCreateReachesTheReplicaWithoutATimeout();
    }

    private void assertCreateReachesTheReplicaWithoutATimeout() throws Exception {
        UUID plannerId = UUID.randomUUID();
        FreshnessScope scope = new FreshnessScope.Entity(plannerId);
        double timeoutsBefore = waitTimeouts();

        create(owner(), plannerId);

        Map<Object, Object> onThePrimary = primaryRedis.opsForHash().entries(scope.key());
        assertThat(onThePrimary).as("marker %s on the primary", scope.key()).isNotEmpty();
        assertThat(localRedis.opsForHash().entries(scope.key()))
                .as("marker %s on the replica when the response arrives", scope.key())
                .isEqualTo(onThePrimary);
        assertThat(waitTimeouts() - timeoutsBefore).isZero();
    }

    private void create(Cookie[] owner, UUID plannerId) throws Exception {
        MvcResult result = mockMvc.perform(put(PLANNERS + plannerId).with(withCsrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                UpsertRequests.draft().id(plannerId.toString()).title("marker replication").build()))
                        .cookie(owner))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("create answered %s", result.getResponse().getContentAsString())
                .isBetween(200, 299);
    }

    private Cookie[] owner() {
        User user = TestDataFactory.createTestUser(userRepository,
                "marker-replication-" + UUID.randomUUID() + "@example.com");
        return new Cookie[] {
            AuthCookies.accessToken(TestDataFactory.generateAccessToken(jwtTokenService, user)),
            AuthCookies.freshDeviceId()};
    }

    private double waitTimeouts() {
        return meterRegistry.counter(MarkerStore.WAIT_TIMEOUTS).count();
    }

    private static Properties info(StringRedisTemplate template) {
        return template.execute((RedisCallback<Properties>) connection -> connection.serverCommands().info("replication"));
    }
}

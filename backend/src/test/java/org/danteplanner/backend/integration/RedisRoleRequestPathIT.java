package org.danteplanner.backend.integration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.resource.ClientResources;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.config.BoundedRedisConnections;
import org.danteplanner.backend.shared.config.DeviceIdResolver;
import org.danteplanner.backend.shared.config.FrontendProperties;
import org.danteplanner.backend.shared.config.LoginRedirect;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.shared.ratelimit.RateLimitInterceptor;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.shared.ratelimit.RateLimitProperties;
import org.danteplanner.backend.shared.ratelimit.RateLimitService;
import org.danteplanner.backend.shared.ratelimit.RateLimitStore;
import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;
import org.danteplanner.backend.shared.redis.RedisRoleProbe;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.RedisEndpoints;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.ratelimitfixture.DeclaredHandlerFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class RedisRoleRequestPathIT extends ReplicaRoutingHarnessSupport {

    private static final Duration ONE_COMMAND_WAIT = BoundedRedisConnections.COMMAND_TIMEOUT;

    private static final String SETTINGS_PATH = "/api/user/settings";
    private static final String PLANNER_PATH = "/api/planner/md/";
    private static final String PUBLISHED_LIST_PATH = "/api/planner/md/published";
    private static final String GOOGLE_CALLBACK_PATH = "/api/auth/google/callback";

    private static final String BLACKLIST_SKIPPED = "blacklist_check_skipped_total";
    private static final String TOMBSTONE_SKIPPED = "tombstone.check_skipped";
    private static final String CHARGE_SKIPPED = "rate_limit.charge_skipped";
    private static final String FIXTURE_PATH = "/api/fixture/inherited";
    private static final String FIXTURE_ENDPOINT = "fixture";
    private static final int CONCURRENT_REQUESTS = 20;
    private static final Duration REVOCATION_LIFETIME = Duration.ofHours(1);
    private static final Duration BUCKET_TTL = Duration.ofMinutes(5);
    private static final Pattern REPLICATION_OFFSET = Pattern.compile("master_repl_offset:(\\d+)");

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final String deviceId = UUID.randomUUID().toString();

    @LocalServerPort
    private int port;

    @Autowired
    private RedisRoleHealth roleHealth;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    @Qualifier("authLocalRedisConnectionFactory")
    private LettuceConnectionFactory authLocalRedisConnectionFactory;

    @Autowired
    private ClientResources redisClientResources;

    @Autowired
    private RateLimitProperties rateLimitProperties;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private DeviceIdResolver deviceIdResolver;

    @Autowired
    private FrontendProperties frontendProperties;

    @Autowired
    private ProblemWriter problemWriter;

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void revokedToken_WhenAuthLocalIsOpenAndThePrimaryAnswers_IsRefusedByThePrimaryWhileOthersPassAtOnce()
            throws IOException {
        String token = TestDataFactory.generateAccessToken(jwtTokenService,
                TestDataFactory.createTestUser(userRepository, "role-local-open@example.com"));
        String revoked = TestDataFactory.generateAccessToken(jwtTokenService,
                TestDataFactory.createTestUser(userRepository, "role-local-open-revoked@example.com"));
        tokenBlacklistService.blacklistToken(revoked, expiresInAnHour());
        awaitAvailable(RedisRole.AUTH_LOCAL, RedisRole.AUTH);

        blackholeAuthLocal();
        try {
            Hang.until(() -> !roleHealth.isAvailable(RedisRole.AUTH_LOCAL), "the probe opening redis-auth-local");
            assertThat(roleHealth.isAvailable(RedisRole.AUTH)).isTrue();
            double blacklistBefore = counter(BLACKLIST_SKIPPED);

            assertThat(send(SETTINGS_PATH, revoked).status()).isEqualTo(401);
            Sample unrevoked = send(SETTINGS_PATH, token);

            assertThat(unrevoked.status()).isEqualTo(200);
            assertThat(unrevoked.elapsed()).isLessThan(ONE_COMMAND_WAIT);
            assertThat(counter(BLACKLIST_SKIPPED)).isEqualTo(blacklistBefore);
        } finally {
            healAuthLocal();
        }
        awaitAvailable(RedisRole.AUTH_LOCAL);
    }

    @Test
    void concurrentReads_WhenTheAuthLocalConnectionIsLostBehindItsBlackhole_AllAnswerWithoutWaiting()
            throws IOException {
        User user = TestDataFactory.createTestUser(userRepository, "role-reconnect@example.com");
        String token = TestDataFactory.generateAccessToken(jwtTokenService, user);
        awaitAvailable(RedisRole.AUTH_LOCAL, RedisRole.AUTH);

        blackholeAuthLocal();
        try {
            Hang.until(() -> !roleHealth.isAvailable(RedisRole.AUTH_LOCAL), "the probe opening redis-auth-local");
            authLocalRedisConnectionFactory.resetConnection();

            List<CompletableFuture<Sample>> requests = IntStream.range(0, CONCURRENT_REQUESTS)
                    .mapToObj(request -> sendAsync(SETTINGS_PATH, token))
                    .toList();
            List<Sample> samples = requests.stream().map(request -> await(request, "a concurrent GET")).toList();

            assertThat(samples).hasSize(CONCURRENT_REQUESTS).allSatisfy(sample -> {
                assertThat(sample.status()).isEqualTo(200);
                assertThat(sample.elapsed()).isLessThan(ONE_COMMAND_WAIT);
            });
        } finally {
            healAuthLocal();
        }
        awaitAvailable(RedisRole.AUTH_LOCAL);
    }

    @Test
    void revocation_WhenAuthLocalHasRecoveredAndHoldsIt_IsRefusedOnTheTokensFirstUse() throws IOException {
        User user = TestDataFactory.createTestUser(userRepository, "role-recovery@example.com");
        awaitAvailable(RedisRole.AUTH_LOCAL);
        blackholeAuthLocal();
        try {
            Hang.until(() -> !roleHealth.isAvailable(RedisRole.AUTH_LOCAL), "the probe opening redis-auth-local");
        } finally {
            healAuthLocal();
        }
        awaitAvailable(RedisRole.AUTH_LOCAL);

        String revoked = TestDataFactory.generateAccessToken(jwtTokenService, user);
        tokenBlacklistService.blacklistToken(revoked, expiresInAnHour());
        long written = replicationOffset(AUTH_REDIS);
        Hang.until(() -> replicationOffset(AUTH_REDIS_REPLICA) >= written,
                "the auth-local replica applying the revocation");

        assertThat(send(SETTINGS_PATH, revoked).status()).isEqualTo(401);
    }

    @Test
    void probeAtStartup_WhenTheRateLimitStoreIsReachable_ConnectsItSoTheFirstChargeIsMetered() throws Exception {
        RedisRoleHealth health = new RedisRoleHealth();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        try (RateLimitStore store = rateLimitStore();
                RedisRoleProbe probe = probeOf(store, health)) {
            probe.probeAtStartup();

            assertThat(health.isAvailable(RedisRole.RATE_LIMIT)).isTrue();
            assertThat(store.isConnected()).isTrue();
            long userId = authenticate();
            fixtureMvc(store, health, meters).perform(get(FIXTURE_PATH)).andExpect(status().isOk());

            assertThat(meters.find(CHARGE_SKIPPED).counter()).isNull();
            assertThat(store.getProxyConfiguration(bucketKey(userId))).isPresent();
        }
    }

    @Test
    void rateLimitStore_WhenUnreachableAtStartThenUp_ServesUnmeteredUntilTheProbeConnectsItThenMeters()
            throws Exception {
        RedisRoleHealth health = new RedisRoleHealth();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        long userId = authenticate();
        try (RateLimitStore store = rateLimitStore();
                RedisRoleProbe probe = probeOf(store, health)) {
            freeze(RATE_LIMIT_REDIS);
            try {
                probe.probeAtStartup();

                assertThat(health.isAvailable(RedisRole.RATE_LIMIT)).isFalse();
                assertThat(store.isConnected()).isFalse();
                long start = System.nanoTime();
                assertThat(catchThrowable(store::builder)).isInstanceOf(RedisConnectionException.class);
                assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(ONE_COMMAND_WAIT);
                fixtureMvc(store, health, meters).perform(get(FIXTURE_PATH)).andExpect(status().isOk());
                assertThat(meters.counter(CHARGE_SKIPPED, "policy", RateLimitPolicy.CRUD.name()).count())
                        .isEqualTo(1.0);
            } finally {
                thaw(RATE_LIMIT_REDIS);
            }

            Hang.until(() -> {
                probe.probe();
                return health.isAvailable(RedisRole.RATE_LIMIT);
            }, "the runtime probe connecting the rate-limit store and closing its breaker");

            assertThat(store.isConnected()).isTrue();
            fixtureMvc(store, health, meters).perform(get(FIXTURE_PATH)).andExpect(status().isOk());
            assertThat(meters.counter(CHARGE_SKIPPED, "policy", RateLimitPolicy.CRUD.name()).count())
                    .isEqualTo(1.0);
            assertThat(store.getProxyConfiguration(bucketKey(userId))).isPresent();
        }
        awaitAvailable(RedisRole.RATE_LIMIT);
    }

    @Test
    void authenticatedReads_WhenBothAuthRolesAreOpen_AnswerWithoutWaitingAndCountEverySkippedCheck()
            throws IOException {
        User user = TestDataFactory.createTestUser(userRepository, "role-both-open@example.com");
        String token = TestDataFactory.generateAccessToken(jwtTokenService, user);
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, user, false);
        replicationControl.awaitCaughtUp();
        awaitAvailable(RedisRole.AUTH_LOCAL, RedisRole.AUTH);

        blackholeAuthLocal();
        freeze(AUTH_REDIS);
        try {
            Hang.until(() -> !roleHealth.isAvailable(RedisRole.AUTH_LOCAL)
                    && !roleHealth.isAvailable(RedisRole.AUTH), "the probe opening both auth role breakers");

            double blacklistBefore = counter(BLACKLIST_SKIPPED);
            Sample settings = send(SETTINGS_PATH, token);
            assertThat(settings.status()).isEqualTo(200);
            assertThat(settings.elapsed()).isLessThan(ONE_COMMAND_WAIT);
            assertThat(counter(BLACKLIST_SKIPPED) - blacklistBefore).isEqualTo(2.0);

            blacklistBefore = counter(BLACKLIST_SKIPPED);
            double tombstoneBefore = counter(TOMBSTONE_SKIPPED);
            Sample byId = send(PLANNER_PATH + planner.getId(), token);
            assertThat(byId.status()).isEqualTo(200);
            assertThat(byId.elapsed()).isLessThan(ONE_COMMAND_WAIT);
            assertThat(counter(BLACKLIST_SKIPPED) - blacklistBefore).isEqualTo(2.0);
            assertThat(counter(TOMBSTONE_SKIPPED) - tombstoneBefore).isEqualTo(1.0);
        } finally {
            thaw(AUTH_REDIS);
            healAuthLocal();
            awaitAuthRedisReplicaLinked();
        }
        awaitAvailable(RedisRole.AUTH_LOCAL, RedisRole.AUTH);
    }

    @Test
    void rateLimitedRequests_WhenTheRateLimitRoleIsOpen_AnswerAtOnceUnmeteredOrRefusedByPolicy() {
        awaitAvailable(RedisRole.RATE_LIMIT);

        freeze(RATE_LIMIT_REDIS);
        try {
            Hang.until(() -> !roleHealth.isAvailable(RedisRole.RATE_LIMIT), "the probe opening redis-rate-limit");

            double skippedBefore = chargeSkipped(RateLimitPolicy.PUBLIC_READ);
            Sample publicRead = send(PUBLISHED_LIST_PATH, null);
            assertThat(publicRead.status()).isEqualTo(200);
            assertThat(publicRead.elapsed()).isLessThan(ONE_COMMAND_WAIT);
            assertThat(chargeSkipped(RateLimitPolicy.PUBLIC_READ) - skippedBefore).isEqualTo(1.0);

            Sample login = send(GOOGLE_CALLBACK_PATH, null);
            assertThat(login.status()).isEqualTo(302);
            assertThat(login.location()).endsWith(LoginRedirect.UNAVAILABLE);
            assertThat(login.elapsed()).isLessThan(ONE_COMMAND_WAIT);
        } finally {
            thaw(RATE_LIMIT_REDIS);
        }
        awaitAvailable(RedisRole.RATE_LIMIT);
    }

    private RateLimitStore rateLimitStore() {
        return new RateLimitStore(redisClientResources, RATE_LIMIT_REDIS.getRedisHost(),
                RATE_LIMIT_REDIS.getRedisPort(), BUCKET_TTL);
    }

    private RedisRoleProbe probeOf(RateLimitStore store, RedisRoleHealth health) {
        return new RedisRoleProbe(redisClientResources,
                RedisEndpoints.everyRoleAt(RATE_LIMIT_REDIS.getRedisHost(), RATE_LIMIT_REDIS.getRedisPort()),
                health, List.of(store));
    }

    private MockMvc fixtureMvc(RateLimitStore store, RedisRoleHealth health, MeterRegistry meters) {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new RateLimitService(store, rateLimitProperties), securityProperties, deviceIdResolver,
                frontendProperties, problemWriter, meters, health);
        return MockMvcBuilders.standaloneSetup(new DeclaredHandlerFixture()).addInterceptors(interceptor).build();
    }

    private static long authenticate() {
        long userId = TestDataFactory.nextUserId();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        return userId;
    }

    private static byte[] bucketKey(long userId) {
        return (userId + ":" + FIXTURE_ENDPOINT).getBytes(StandardCharsets.UTF_8);
    }

    private static Date expiresInAnHour() {
        return new Date(System.currentTimeMillis() + REVOCATION_LIFETIME.toMillis());
    }

    private static long replicationOffset(GenericContainer<?> redis) {
        try {
            Matcher offset = REPLICATION_OFFSET.matcher(
                    redis.execInContainer("redis-cli", "INFO", "replication").getStdout());
            assertThat(offset.find()).as("INFO replication carries master_repl_offset").isTrue();
            return Long.parseLong(offset.group(1));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void awaitAvailable(RedisRole... roles) {
        Hang.until(() -> List.of(roles).stream().allMatch(roleHealth::isAvailable),
                "the probe closing " + List.of(roles));
    }

    private Sample send(String path, String token) {
        return await(sendAsync(path, token), "GET " + path);
    }

    private static Sample await(CompletableFuture<Sample> request, String what) {
        try {
            return Hang.get(request, what);
        } catch (ExecutionException e) {
            throw new IllegalStateException(what + " failed", e.getCause());
        }
    }

    private CompletableFuture<Sample> sendAsync(String path, String token) {
        String cookies = CookieConstants.DEVICE_ID + "=" + deviceId
                + (token == null ? "" : "; " + CookieConstants.ACCESS_TOKEN + "=" + token);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.COOKIE, cookies)
                .GET()
                .build();
        long start = System.nanoTime();
        return http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> new Sample(response.statusCode(),
                        response.headers().firstValue(HttpHeaders.LOCATION).orElse(""),
                        Duration.ofNanos(System.nanoTime() - start)));
    }

    private double counter(String name) {
        return meterRegistry.find(name).counters().stream().mapToDouble(Counter::count).sum();
    }

    private double chargeSkipped(RateLimitPolicy policy) {
        return meterRegistry.find(CHARGE_SKIPPED).tag("policy", policy.name()).counters().stream()
                .mapToDouble(Counter::count).sum();
    }

    private static void freeze(GenericContainer<?> container) {
        container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
    }

    private static void thaw(GenericContainer<?> container) {
        container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
    }

    private record Sample(int status, String location, Duration elapsed) {
    }
}

package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.Toxic;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import javax.sql.DataSource;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.shared.sse.SseService;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.toxiproxy.ToxiproxyContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recovery-after-restore acceptance tests for every Spring↔Redis connection.
 *
 * <p>{@code DegradationIT} proves the app degrades by operation while a Redis route is
 * severed; this suite proves the complementary contract — each connection <b>resumes
 * service on its own</b> once the route heals, without a pod restart:</p>
 *
 * <ul>
 *   <li><b>Auth blacklist</b>: fail-open is a security debt that must be repaid — a token
 *       blacklisted before the outage must be rejected again once auth Redis returns.</li>
 *   <li><b>SSE pub/sub</b>: the {@code RedisMessageListenerContainer} must re-subscribe
 *       after its subscription connection is severed, or cross-node fan-out silently dies
 *       until the pod restarts (the startup-unreachable case is documented as fatal in
 *       {@code SseSubscriberConfig}; the mid-life case must self-heal).</li>
 *   <li><b>Rate limiting</b>: the typed 503 during the outage must give way to normal
 *       request handling, not persist.</li>
 * </ul>
 *
 * <p>Self-contained harness per the {@code DegradationIT} precedent: the JVM-shared
 * containers of the causal harness are never toxic-ed. One dedicated Redis serves all
 * three roles behind three independently severable Toxiproxy routes. Recovery is
 * asynchronous by nature (Lettuce reconnects and the listener container's recovery loop
 * run on their own schedules), so outcomes are asserted through bounded polls on the
 * observed behavior — never a bare sleep-then-assert.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class RedisConnectionRecoveryIT {

    private static final String MYSQL_IMAGE = "mysql:8.0";
    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final String TOXIPROXY_IMAGE = "ghcr.io/shopify/toxiproxy:2.5.0";

    private static final int REDIS_INTERNAL_PORT = 6379;
    private static final int MYSQL_INTERNAL_PORT = 3306;
    private static final String PRIMARY_ALIAS = "recovery-mysql-primary";
    private static final String DEDICATED_REDIS_ALIAS = "recovery-redis";

    private static final String AUTH_REDIS_PROXY_NAME = "recovery-app-to-auth-redis";
    private static final int AUTH_PROXY_LISTEN_PORT = 8666;
    private static final String RATE_LIMIT_REDIS_PROXY_NAME = "recovery-app-to-rate-limit-redis";
    private static final int RATE_LIMIT_PROXY_LISTEN_PORT = 8667;
    private static final String SSE_LOCAL_REDIS_PROXY_NAME = "recovery-app-to-sse-local-redis";
    private static final int SSE_LOCAL_PROXY_LISTEN_PORT = 8668;

    private static final String ROOT_USER = "root";
    private static final String REPL_USER = "repl";
    private static final String REPL_PASSWORD = "repl-pass";

    private static final long ONE_HOUR_MS = 3_600_000L;
    private static final long RECOVERY_DEADLINE_MS = 30_000L;
    private static final long POLL_INTERVAL_MS = 250L;
    private static final int SSE_REPUBLISH_ATTEMPTS = 20;
    private static final long SSE_DELIVERY_WAIT_MS = 1_000L;

    /** Minimal planner content that passes {@code PlannerContentValidator} (from DegradationIT). */
    private static final String VALID_CONTENT = """
        {
            "selectedKeywords":[],
            "selectedBuffIds":[100,201],
            "selectedGiftKeyword":"Combustion",
            "selectedGiftIds":["9001"],
            "equipment":{
                "01":{"identity":{"id":"10101","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20101","threadspin":4}}},
                "02":{"identity":{"id":"10201","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20201","threadspin":4}}},
                "03":{"identity":{"id":"10301","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20301","threadspin":4}}},
                "04":{"identity":{"id":"10401","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20401","threadspin":4}}},
                "05":{"identity":{"id":"10501","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20501","threadspin":4}}},
                "06":{"identity":{"id":"10601","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20601","threadspin":4}}},
                "07":{"identity":{"id":"10701","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20701","threadspin":4}}},
                "08":{"identity":{"id":"10801","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20801","threadspin":4}}},
                "09":{"identity":{"id":"10901","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20901","threadspin":4}}},
                "10":{"identity":{"id":"11001","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"21001","threadspin":4}}},
                "11":{"identity":{"id":"11101","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"21101","threadspin":4}}},
                "12":{"identity":{"id":"11201","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"21201","threadspin":4}}}
            },
            "deploymentOrder":[0,1,2,3,4,5],
            "floorSelections":[{"themePackId":"1001","difficulty":0,"giftIds":["9002"]}],
            "sectionNotes":{}
        }
        """.trim().replace("\n", "").replace(" ", "");

    private static final Network RECOVERY_NETWORK = Network.newNetwork();

    static final MySQLContainer PRIMARY = new MySQLContainer(MYSQL_IMAGE)
            .withNetwork(RECOVERY_NETWORK)
            .withNetworkAliases(PRIMARY_ALIAS)
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withCommand(
                    "--server-id=1",
                    "--log-bin=mysql-bin",
                    "--binlog-format=ROW",
                    "--gtid-mode=ON",
                    "--enforce-gtid-consistency=ON");

    static final MySQLContainer REPLICA = new MySQLContainer(MYSQL_IMAGE)
            .withNetwork(RECOVERY_NETWORK)
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withCommand(
                    "--server-id=2",
                    "--log-bin=mysql-bin",
                    "--binlog-format=ROW",
                    "--gtid-mode=ON",
                    "--enforce-gtid-consistency=ON");

    static final GenericContainer<?> DEDICATED_REDIS = new GenericContainer<>(
            DockerImageName.parse(REDIS_IMAGE))
            .withNetwork(RECOVERY_NETWORK)
            .withNetworkAliases(DEDICATED_REDIS_ALIAS)
            .withExposedPorts(REDIS_INTERNAL_PORT);

    static final ToxiproxyContainer RECOVERY_TOXIPROXY = new ToxiproxyContainer(TOXIPROXY_IMAGE)
            .withNetwork(RECOVERY_NETWORK);

    static final Proxy AUTH_REDIS_PROXY;

    static final Proxy RATE_LIMIT_REDIS_PROXY;

    static final Proxy SSE_LOCAL_REDIS_PROXY;

    static {
        PRIMARY.start();
        REPLICA.start();
        DEDICATED_REDIS.start();
        RECOVERY_TOXIPROXY.start();
        AUTH_REDIS_PROXY = createRedisProxy(AUTH_REDIS_PROXY_NAME, AUTH_PROXY_LISTEN_PORT);
        RATE_LIMIT_REDIS_PROXY = createRedisProxy(RATE_LIMIT_REDIS_PROXY_NAME, RATE_LIMIT_PROXY_LISTEN_PORT);
        SSE_LOCAL_REDIS_PROXY = createRedisProxy(SSE_LOCAL_REDIS_PROXY_NAME, SSE_LOCAL_PROXY_LISTEN_PORT);
        wireReplication(new JdbcTemplate(adminDataSource(PRIMARY)), new JdbcTemplate(adminDataSource(REPLICA)));
    }

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private SsePublisher ssePublisher;

    @MockitoSpyBean
    private SseService sseService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @DynamicPropertySource
    static void harnessProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PRIMARY::getJdbcUrl);
        registry.add("spring.datasource.username", PRIMARY::getUsername);
        registry.add("spring.datasource.password", PRIMARY::getPassword);
        registry.add("spring.flyway.url", PRIMARY::getJdbcUrl);
        registry.add("spring.flyway.user", PRIMARY::getUsername);
        registry.add("spring.flyway.password", PRIMARY::getPassword);

        registry.add("datasource.routing.enabled", () -> "true");
        registry.add("datasource.replica.enabled", () -> "true");
        registry.add("datasource.replica.url", REPLICA::getJdbcUrl);
        registry.add("datasource.replica.username", REPLICA::getUsername);
        registry.add("datasource.replica.password", REPLICA::getPassword);

        registry.add("redis.auth.host", RECOVERY_TOXIPROXY::getHost);
        registry.add("redis.auth.port", () -> RECOVERY_TOXIPROXY.getMappedPort(AUTH_PROXY_LISTEN_PORT));

        registry.add("redis.auth-local.host", RECOVERY_TOXIPROXY::getHost);
        registry.add("redis.auth-local.port", () -> RECOVERY_TOXIPROXY.getMappedPort(AUTH_PROXY_LISTEN_PORT));

        registry.add("redis.rate-limit.host", RECOVERY_TOXIPROXY::getHost);
        registry.add("redis.rate-limit.port", () -> RECOVERY_TOXIPROXY.getMappedPort(RATE_LIMIT_PROXY_LISTEN_PORT));

        registry.add("redis.sse-local.host", RECOVERY_TOXIPROXY::getHost);
        registry.add("redis.sse-local.port", () -> RECOVERY_TOXIPROXY.getMappedPort(SSE_LOCAL_PROXY_LISTEN_PORT));
    }

    @AfterEach
    void restoreAllRedisRoutes() throws IOException {
        for (Toxic toxic : AUTH_REDIS_PROXY.toxics().getAll()) {
            toxic.remove();
        }
        AUTH_REDIS_PROXY.enable();
        RATE_LIMIT_REDIS_PROXY.enable();
        SSE_LOCAL_REDIS_PROXY.enable();
    }

    @Test
    @DisplayName("Auth Redis restored: a token blacklisted before the outage is rejected again without a restart")
    void blacklistedToken_WhenAuthRedisRestored_IsRejectedAgain() throws IOException {
        String token = "recovery-blacklist-token-" + UUID.randomUUID();
        tokenBlacklistService.blacklistToken(token, new Date(System.currentTimeMillis() + ONE_HOUR_MS));
        assertThat(tokenBlacklistService.isBlacklisted(token))
                .as("precondition: the token is rejected while auth Redis is UP")
                .isTrue();

        AUTH_REDIS_PROXY.toxics().timeout("recovery-auth-redis-cut", ToxicDirection.UPSTREAM, 0);
        assertThat(tokenBlacklistService.isBlacklisted(token))
                .as("fail-open during the outage: the check must not deny")
                .isFalse();

        for (Toxic toxic : AUTH_REDIS_PROXY.toxics().getAll()) {
            toxic.remove();
        }

        assertThat(awaitTrue(() -> tokenBlacklistService.isBlacklisted(token)))
                .as("the fail-open window must close once auth Redis is reachable again: "
                        + "the blacklisted token is rejected without a pod restart")
                .isTrue();
    }

    @Test
    @DisplayName("SSE-local Redis restored: the pub/sub container re-subscribes and fan-out delivery resumes")
    void sseFanout_WhenSubscriberConnectionRestored_ResubscribesAndDelivers() throws IOException {
        Long userId = 4243L;

        ssePublisher.publishUserEvent(userId, null, SseEventType.UPDATED, "recovery-warmup",
                Map.of("id", "recovery-warmup"));
        verify(sseService, timeout(10_000)).sendToUser(eq(userId), isNull(), eq("updated"), any());

        SSE_LOCAL_REDIS_PROXY.disable();
        SSE_LOCAL_REDIS_PROXY.enable();
        clearInvocations(sseService);

        // The listener container's recovery loop re-subscribes on its own schedule, and only
        // events published AFTER the re-subscription are delivered — so publish fresh events
        // until one arrives, bounded by the attempt budget.
        boolean delivered = false;
        for (int attempt = 0; attempt < SSE_REPUBLISH_ATTEMPTS && !delivered; attempt++) {
            ssePublisher.publishUserEvent(userId, null, SseEventType.UPDATED,
                    "recovery-attempt-" + attempt, Map.of("id", "recovery-attempt-" + attempt));
            try {
                verify(sseService, timeout(SSE_DELIVERY_WAIT_MS).atLeastOnce())
                        .sendToUser(eq(userId), isNull(), eq("updated"), any());
                delivered = true;
            } catch (AssertionError notYetResubscribed) {
                delivered = false;
            }
        }

        assertThat(delivered)
                .as("cross-node fan-out must self-heal after a severed subscriber connection — "
                        + "a dead subscription until pod restart silently disables multi-device sync")
                .isTrue();
    }

    @Test
    @DisplayName("Rate-limit Redis restored: the typed 503 gives way to normal request handling")
    void rateLimitedWrite_WhenRateLimitRedisRestored_StopsReturning503() throws Exception {
        User author = TestDataFactory.createTestUser(
                userRepository, "recovery-rate-limit-" + UUID.randomUUID() + "@example.com");
        Cookie auth = new Cookie("accessToken",
                TestDataFactory.generateAccessToken(jwtTokenService, author));
        Cookie device = new Cookie("deviceId", UUID.randomUUID().toString());

        RATE_LIMIT_REDIS_PROXY.disable();

        UUID blockedPlannerId = UUID.randomUUID();
        mockMvc.perform(put("/api/planner/md/" + blockedPlannerId).with(withCsrf())
                        .cookie(auth, device)
                        .contentType(APPLICATION_JSON)
                        .content(upsertBody(blockedPlannerId, "recovery-during-outage")))
                .andExpect(status().isServiceUnavailable());

        RATE_LIMIT_REDIS_PROXY.enable();

        assertThat(awaitTrue(() -> {
            try {
                UUID plannerId = UUID.randomUUID();
                int status = mockMvc.perform(put("/api/planner/md/" + plannerId).with(withCsrf())
                                .cookie(auth, device)
                                .contentType(APPLICATION_JSON)
                                .content(upsertBody(plannerId, "recovery-after-restore")))
                        .andReturn().getResponse().getStatus();
                return status >= 200 && status < 300;
            } catch (Exception e) {
                return false;
            }
        }))
                .as("rate-limited writes must succeed again once rate-limit Redis is reachable")
                .isTrue();
    }

    /**
     * Polls the condition until it holds or {@link #RECOVERY_DEADLINE_MS} elapses. Recovery
     * is asynchronous (Lettuce reconnect, listener-container recovery loop), so the observed
     * outcome is the only deterministic signal — the poll asserts behavior, never timing.
     */
    private static boolean awaitTrue(BooleanSupplier condition) {
        long deadline = System.nanoTime() + RECOVERY_DEADLINE_MS * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    private String upsertBody(UUID id, String title) throws IOException {
        UpsertPlannerRequest request = new UpsertPlannerRequest(
                id.toString(), "5F", title, PlannerStatus.DRAFT, VALID_CONTENT, 7,
                PlannerType.MIRROR_DUNGEON, null, null);
        return objectMapper.writeValueAsString(request);
    }

    private static Proxy createRedisProxy(String name, int listenPort) {
        try {
            ToxiproxyClient client = new ToxiproxyClient(
                    RECOVERY_TOXIPROXY.getHost(), RECOVERY_TOXIPROXY.getControlPort());
            return client.createProxy(
                    name,
                    "0.0.0.0:" + listenPort,
                    DEDICATED_REDIS_ALIAS + ":" + REDIS_INTERNAL_PORT);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create Toxiproxy route " + name, e);
        }
    }

    private static void wireReplication(JdbcTemplate primary, JdbcTemplate replica) {
        primary.execute("CREATE USER IF NOT EXISTS '" + REPL_USER + "'@'%' "
                + "IDENTIFIED WITH mysql_native_password BY '" + REPL_PASSWORD + "'");
        primary.execute("GRANT REPLICATION SLAVE ON *.* TO '" + REPL_USER + "'@'%'");
        primary.execute("FLUSH PRIVILEGES");

        String primaryGtid = primary.queryForObject("SELECT @@GLOBAL.gtid_executed", String.class);

        replica.execute("STOP REPLICA");
        replica.execute("RESET REPLICA ALL");
        replica.execute("RESET MASTER");
        if (primaryGtid != null && !primaryGtid.isBlank()) {
            replica.execute("SET GLOBAL gtid_purged = '" + primaryGtid.replaceAll("\\s+", "") + "'");
        }
        replica.execute("CHANGE REPLICATION SOURCE TO "
                + "SOURCE_HOST='" + PRIMARY_ALIAS + "', "
                + "SOURCE_PORT=" + MYSQL_INTERNAL_PORT + ", "
                + "SOURCE_USER='" + REPL_USER + "', "
                + "SOURCE_PASSWORD='" + REPL_PASSWORD + "', "
                + "SOURCE_AUTO_POSITION=1");
        replica.execute("START REPLICA");
    }

    private static DataSource adminDataSource(MySQLContainer container) {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource();
        dataSource.setDriverClassName(container.getDriverClassName());
        dataSource.setUrl(container.getJdbcUrl());
        dataSource.setUsername(ROOT_USER);
        dataSource.setPassword(container.getPassword());
        dataSource.setSuppressClose(true);
        return dataSource;
    }
}

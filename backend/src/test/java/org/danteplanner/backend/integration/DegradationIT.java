package org.danteplanner.backend.integration;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.Toxic;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Date;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.config.TestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.toxiproxy.ToxiproxyContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase-5 acceptance test (INV6 fail-open ladder). Proves the two external-contract
 * assertions for the blacklist externalization to Redis:
 *
 * <ul>
 *   <li>(a) <b>Fail-open + counter</b>: with the app's auth Redis path cut, a blacklist check
 *       still returns cleanly (does not throw / does not deny) AND the Micrometer counter
 *       {@code blacklist_check_skipped_total} increments.</li>
 *   <li>(b) <b>AOF-replay integrity</b>: a token blacklisted while auth Redis is UP is STILL
 *       rejected after the auth Redis reloads its dataset from the AOF ({@code DEBUG LOADAOF}).</li>
 * </ul>
 *
 * <p><b>Isolation (Option B):</b> DegradationIT does NOT extend {@link CausalHarnessSupport};
 * it stands up its own self-contained harness — a single MySQL primary plus a dedicated auth
 * Redis ({@code --appendonly yes --appendfsync always --enable-debug-command local}) behind a
 * dedicated Toxiproxy — so the JVM-shared {@code AUTH_REDIS} of the causal harness is never
 * toxic-ed, stopped, or AOF-corrupted for sibling ITs. Option A (a subclass
 * {@link DynamicPropertySource} overriding the base) was rejected: the base registration wins,
 * so the app stayed bound to the shared {@code AUTH_REDIS}.</p>
 *
 * <p>Determinism (INV4): the cut is asserted through the observed fail-open outcome and the
 * Toxiproxy control API, never a wall-clock timeout; {@code appendfsync always} makes the AOF
 * replay deterministic — no sleeps.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class DegradationIT {

    private static final String MYSQL_IMAGE = "mysql:8.0";
    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final int REDIS_INTERNAL_PORT = 6379;
    private static final String DEDICATED_AUTH_REDIS_ALIAS = "degradation-auth-redis";
    private static final int PROXY_LISTEN_PORT = 8666;

    private static final String TOXIPROXY_IMAGE = "ghcr.io/shopify/toxiproxy:2.5.0";

    private static final String COUNTER_NAME = "blacklist_check_skipped_total";
    private static final long ONE_HOUR_MS = 3_600_000L;

    private static final Network DEGRADATION_NETWORK = Network.newNetwork();

    static final MySQLContainer PRIMARY = new MySQLContainer(MYSQL_IMAGE)
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    static final GenericContainer<?> DEDICATED_AUTH_REDIS = new GenericContainer<>(
            DockerImageName.parse(REDIS_IMAGE))
            .withNetwork(DEGRADATION_NETWORK)
            .withNetworkAliases(DEDICATED_AUTH_REDIS_ALIAS)
            .withExposedPorts(REDIS_INTERNAL_PORT)
            .withCommand(
                    "redis-server",
                    "--appendonly", "yes",
                    "--appendfsync", "always",
                    "--enable-debug-command", "local");

    static final ToxiproxyContainer DEGRADATION_TOXIPROXY = new ToxiproxyContainer(TOXIPROXY_IMAGE)
            .withNetwork(DEGRADATION_NETWORK);

    static final Proxy AUTH_REDIS_PROXY;

    static {
        PRIMARY.start();
        DEDICATED_AUTH_REDIS.start();
        DEGRADATION_TOXIPROXY.start();
        AUTH_REDIS_PROXY = createAuthRedisProxy();
    }

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    @Qualifier("authRedisConnectionFactory")
    private LettuceConnectionFactory authRedisConnectionFactory;

    @DynamicPropertySource
    static void harnessProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PRIMARY::getJdbcUrl);
        registry.add("spring.datasource.username", PRIMARY::getUsername);
        registry.add("spring.datasource.password", PRIMARY::getPassword);
        registry.add("spring.flyway.url", PRIMARY::getJdbcUrl);
        registry.add("spring.flyway.user", PRIMARY::getUsername);
        registry.add("spring.flyway.password", PRIMARY::getPassword);

        registry.add("redis.auth.host", DEGRADATION_TOXIPROXY::getHost);
        registry.add("redis.auth.port", () -> DEGRADATION_TOXIPROXY.getMappedPort(PROXY_LISTEN_PORT));

        registry.add("redis.rate-limit.host", DEDICATED_AUTH_REDIS::getHost);
        registry.add("redis.rate-limit.port", () -> DEDICATED_AUTH_REDIS.getMappedPort(REDIS_INTERNAL_PORT));
    }

    @BeforeEach
    void verifyDedicatedAuthRedisWiring() {
        assertThat(authRedisConnectionFactory.getPort())
                .as("Option B: the app must talk to the dedicated Toxiproxy in front of the "
                        + "dedicated AOF auth Redis, not any shared Redis")
                .isEqualTo(DEGRADATION_TOXIPROXY.getMappedPort(PROXY_LISTEN_PORT));
    }

    @AfterEach
    void restoreAuthRedisPath() throws IOException {
        for (Toxic toxic : AUTH_REDIS_PROXY.toxics().getAll()) {
            toxic.remove();
        }
    }

    @Test
    @DisplayName("(a) Auth Redis unreachable: blacklist check fails open (does not throw/deny) and blacklist_check_skipped_total increments")
    void blacklistCheck_WhenAuthRedisUnreachable_FailsOpenAndIncrementsSkipCounter() throws IOException {
        String token = "degradation-fail-open-token";

        double before = skipCounterValue();
        cutAuthRedis();

        boolean blacklisted = tokenBlacklistService.isBlacklisted(token);
        double after = skipCounterValue();

        assertThat(blacklisted)
                .as("fail-open: an unrevoked token must not be denied when auth Redis is unreachable")
                .isFalse();
        assertThat(after - before)
                .as("fail-open must increment " + COUNTER_NAME)
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("(b) AOF-replay integrity: a token blacklisted with Redis UP is still rejected after DEBUG LOADAOF")
    void blacklistedToken_WhenAuthRedisReloadsFromAof_StaysRejected() {
        String token = "degradation-aof-replay-token";
        Date expiry = new Date(System.currentTimeMillis() + ONE_HOUR_MS);

        tokenBlacklistService.blacklistToken(token, expiry);
        assertThat(tokenBlacklistService.isBlacklisted(token))
                .as("precondition: the token is rejected while auth Redis is UP")
                .isTrue();

        reloadAuthRedisFromAof();

        assertThat(tokenBlacklistService.isBlacklisted(token))
                .as("AOF-replay integrity: the blacklist entry must survive DEBUG LOADAOF and stay rejected")
                .isTrue();
    }

    private double skipCounterValue() {
        Counter counter = meterRegistry.find(COUNTER_NAME).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private void cutAuthRedis() throws IOException {
        AUTH_REDIS_PROXY.toxics().timeout("auth-redis-cut", ToxicDirection.UPSTREAM, 0);
    }

    private void reloadAuthRedisFromAof() {
        try {
            Container.ExecResult result =
                    DEDICATED_AUTH_REDIS.execInContainer("redis-cli", "DEBUG", "LOADAOF");
            assertThat(result.getStdout().trim())
                    .as("DEBUG LOADAOF (loopback = local, per --enable-debug-command local) "
                            + "must reload the dedicated auth Redis from its AOF")
                    .contains("OK");
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to issue DEBUG LOADAOF on the dedicated auth Redis", e);
        }
    }

    private static Proxy createAuthRedisProxy() {
        try {
            ToxiproxyClient client = new ToxiproxyClient(
                    DEGRADATION_TOXIPROXY.getHost(), DEGRADATION_TOXIPROXY.getControlPort());
            return client.createProxy(
                    DEDICATED_AUTH_REDIS_ALIAS,
                    "0.0.0.0:" + PROXY_LISTEN_PORT,
                    DEDICATED_AUTH_REDIS_ALIAS + ":" + REDIS_INTERNAL_PORT);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create the dedicated auth-Redis Toxiproxy proxy", e);
        }
    }
}

package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.Toxic;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Date;
import java.util.UUID;
import javax.sql.DataSource;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.toxiproxy.ToxiproxyContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Degradation-by-operation acceptance tests. Two contracts share one self-contained harness:
 *
 * <ul>
 *   <li><b>INV6 fail-open ladder</b> (Phase-5): with the app's auth Redis path cut, a blacklist
 *       check still returns cleanly (fail-open) and increments {@code blacklist_check_skipped_total},
 *       and a token blacklisted while auth Redis was UP survives an AOF reload ({@code DEBUG LOADAOF}).</li>
 *   <li><b>INV5 typed write degradation + health semantics</b> (Phase-9): with the app's WRITE
 *       (primary) datasource severed by disabling its Toxiproxy proxy while the READ (replica)
 *       datasource stays healthy, readiness stays UP, an HTTP write returns {@code WRITE_TEMPORARILY_UNAVAILABLE}
 *       (503), and a list read still serves from the replica — degrade by operation, not by service.</li>
 * </ul>
 *
 * <p><b>Isolation (Option B):</b> DegradationIT does NOT extend {@link CausalHarnessSupport}; it
 * stands up its own self-contained harness — a MySQL primary/replica GTID pair plus a dedicated auth
 * Redis ({@code --appendonly yes --appendfsync always --enable-debug-command local}) behind a
 * dedicated Toxiproxy — so the JVM-shared containers of the causal harness are never toxic-ed,
 * stopped, or AOF-corrupted for sibling ITs. The GTID recipe (primary/replica binlog args,
 * app→primary DB proxy, {@code wireReplication}) is copied from {@link CausalHarnessSupport} rather
 * than inherited, to keep this harness's toxics off the shared containers.</p>
 *
 * <p>Determinism (INV4): every cut is asserted through the observed outcome and the Toxiproxy
 * control API, never a wall-clock timeout — the primary write path fails within Hikari's
 * production connection-timeout (a production constant, not a test timing window), and replication
 * readiness is gated by {@link ReplicationControl#awaitCaughtUp()}; {@code appendfsync always}
 * makes the AOF replay deterministic. No sleeps.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import({TestConfig.class, DegradationIT.HarnessDataSourceConfig.class})
class DegradationIT {

    private static final String MYSQL_IMAGE = "mysql:8.0";
    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final int REDIS_INTERNAL_PORT = 6379;
    private static final String DEDICATED_AUTH_REDIS_ALIAS = "degradation-auth-redis";
    private static final int PROXY_LISTEN_PORT = 8666;

    private static final String PRIMARY_ALIAS = "degradation-mysql-primary";
    private static final int MYSQL_INTERNAL_PORT = 3306;
    private static final String PRIMARY_DB_PROXY_NAME = "degradation-app-to-primary";
    private static final int PRIMARY_DB_PROXY_LISTEN_PORT = 8667;
    private static final String ROOT_USER = "root";
    private static final String REPL_USER = "repl";
    private static final String REPL_PASSWORD = "repl-pass";

    private static final String TOXIPROXY_IMAGE = "ghcr.io/shopify/toxiproxy:2.5.0";

    private static final String COUNTER_NAME = "blacklist_check_skipped_total";
    private static final long ONE_HOUR_MS = 3_600_000L;

    /** Minimal planner content that passes {@code PlannerContentValidator} (from CausalGateIT). */
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

    private static final Network DEGRADATION_NETWORK = Network.newNetwork();

    static final MySQLContainer PRIMARY = new MySQLContainer(MYSQL_IMAGE)
            .withNetwork(DEGRADATION_NETWORK)
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
            .withNetwork(DEGRADATION_NETWORK)
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withCommand(
                    "--server-id=2",
                    "--log-bin=mysql-bin",
                    "--binlog-format=ROW",
                    "--gtid-mode=ON",
                    "--enforce-gtid-consistency=ON");

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

    static final Proxy PRIMARY_DB_PROXY;

    static {
        PRIMARY.start();
        REPLICA.start();
        DEDICATED_AUTH_REDIS.start();
        DEGRADATION_TOXIPROXY.start();
        AUTH_REDIS_PROXY = createAuthRedisProxy();
        PRIMARY_DB_PROXY = createPrimaryDbProxy();
        wireReplication(new JdbcTemplate(adminDataSource(PRIMARY)), new JdbcTemplate(adminDataSource(REPLICA)));
    }

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    @Qualifier("authRedisConnectionFactory")
    private LettuceConnectionFactory authRedisConnectionFactory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    @Qualifier("degradationPrimaryJdbcTemplate")
    private JdbcTemplate degradationPrimaryJdbcTemplate;

    @Autowired
    @Qualifier("degradationReplicaJdbcTemplate")
    private JdbcTemplate degradationReplicaJdbcTemplate;

    @DynamicPropertySource
    static void harnessProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DegradationIT::primaryUrlThroughProxy);
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

        registry.add("redis.auth.host", DEGRADATION_TOXIPROXY::getHost);
        registry.add("redis.auth.port", () -> DEGRADATION_TOXIPROXY.getMappedPort(PROXY_LISTEN_PORT));

        registry.add("redis.rate-limit.host", DEDICATED_AUTH_REDIS::getHost);
        registry.add("redis.rate-limit.port", () -> DEDICATED_AUTH_REDIS.getMappedPort(REDIS_INTERNAL_PORT));
    }

    private static String primaryUrlThroughProxy() {
        return "jdbc:mysql://" + DEGRADATION_TOXIPROXY.getHost() + ":"
                + DEGRADATION_TOXIPROXY.getMappedPort(PRIMARY_DB_PROXY_LISTEN_PORT) + "/testdb";
    }

    @BeforeEach
    void verifyDedicatedAuthRedisWiring() {
        assertThat(authRedisConnectionFactory.getPort())
                .as("Option B: the app must talk to the dedicated Toxiproxy in front of the "
                        + "dedicated AOF auth Redis, not any shared Redis")
                .isEqualTo(DEGRADATION_TOXIPROXY.getMappedPort(PROXY_LISTEN_PORT));
    }

    @AfterEach
    void restoreDegradationPaths() throws IOException {
        for (Toxic toxic : AUTH_REDIS_PROXY.toxics().getAll()) {
            toxic.remove();
        }
        for (Toxic toxic : PRIMARY_DB_PROXY.toxics().getAll()) {
            toxic.remove();
        }
        PRIMARY_DB_PROXY.enable();
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

    /**
     * INV5: with the primary write path severed (Toxiproxy timeout toxic) and the replica read path
     * healthy, the app degrades by operation, not by service — readiness stays UP, an HTTP write
     * returns a typed {@code WRITE_TEMPORARILY_UNAVAILABLE}, and a list read still serves from the
     * replica. A row is seeded (write while healthy) and replication is caught up before the cut so
     * the post-cut read has data; the read carries only the auth cookie (never the write's GTID gate
     * cookie) so {@code @Transactional(readOnly=true)} routes to the healthy replica.
     */
    @Test
    @DisplayName("INV5: primary DB cut → readiness stays UP, write returns WRITE_TEMPORARILY_UNAVAILABLE, list read still serves the replica")
    void primaryDbCut_WhenWriteAttempted_ReturnsWriteTemporarilyUnavailableWhileReadsServe() throws Exception {
        User author = TestDataFactory.createTestUser(
                userRepository, "degradation-inv5-" + UUID.randomUUID() + "@example.com");
        Cookie auth = new Cookie("accessToken",
                TestDataFactory.generateAccessToken(jwtTokenService, author));
        Cookie device = new Cookie("deviceId", UUID.randomUUID().toString());
        UUID seedPlannerId = UUID.randomUUID();

        mockMvc.perform(put("/api/planner/md/" + seedPlannerId).with(withCsrf())
                        .cookie(auth, device)
                        .contentType(APPLICATION_JSON)
                        .content(upsertBody(seedPlannerId, "degradation-inv5-seed")))
                .andExpect(status().is2xxSuccessful());

        ReplicationControl replicationControl =
                new ReplicationControl(degradationPrimaryJdbcTemplate, degradationReplicaJdbcTemplate);
        replicationControl.awaitCaughtUp();

        cutPrimaryDb();

        mockMvc.perform(get("/api/planner/md").cookie(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty());

        UUID writePlannerId = UUID.randomUUID();
        mockMvc.perform(put("/api/planner/md/" + writePlannerId).with(withCsrf())
                        .cookie(auth, device)
                        .contentType(APPLICATION_JSON)
                        .content(upsertBody(writePlannerId, "degradation-inv5-blocked-write")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WRITE_TEMPORARILY_UNAVAILABLE"));

        MvcResult readiness = mockMvc.perform(get("/actuator/health/readiness").cookie(auth))
                .andReturn();
        assertThat(readiness.getResponse().getStatus())
                .as("a primary-DB outage must NEVER flip readiness: /actuator/health/readiness stays 200")
                .isEqualTo(200);
        assertThat(readiness.getResponse().getContentAsString())
                .as("readiness status must be UP during a primary-DB outage")
                .contains("UP");
    }

    private double skipCounterValue() {
        Counter counter = meterRegistry.find(COUNTER_NAME).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private void cutAuthRedis() throws IOException {
        AUTH_REDIS_PROXY.toxics().timeout("auth-redis-cut", ToxicDirection.UPSTREAM, 0);
    }

    private void cutPrimaryDb() throws IOException {
        PRIMARY_DB_PROXY.disable();
    }

    private String upsertBody(UUID id, String title) throws IOException {
        UpsertPlannerRequest request = new UpsertPlannerRequest(
                id.toString(), "5F", title, PlannerStatus.DRAFT, VALID_CONTENT, 7,
                PlannerType.MIRROR_DUNGEON, null, null);
        return objectMapper.writeValueAsString(request);
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

    private static Proxy createPrimaryDbProxy() {
        try {
            ToxiproxyClient client = new ToxiproxyClient(
                    DEGRADATION_TOXIPROXY.getHost(), DEGRADATION_TOXIPROXY.getControlPort());
            return client.createProxy(
                    PRIMARY_DB_PROXY_NAME,
                    "0.0.0.0:" + PRIMARY_DB_PROXY_LISTEN_PORT,
                    PRIMARY_ALIAS + ":" + MYSQL_INTERNAL_PORT);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create the app→primary DB Toxiproxy proxy", e);
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

    /**
     * Exposes the primary and replica as distinct qualified {@link JdbcTemplate} beans, each backed
     * by its own root-privileged connection, so INV5 can drive {@link ReplicationControl} against
     * the same pair the app reads and writes through.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class HarnessDataSourceConfig {

        @Bean
        JdbcTemplate degradationPrimaryJdbcTemplate() {
            return new JdbcTemplate(adminDataSource(PRIMARY));
        }

        @Bean
        JdbcTemplate degradationReplicaJdbcTemplate() {
            return new JdbcTemplate(adminDataSource(REPLICA));
        }
    }
}

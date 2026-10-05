package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.shared.config.FallbackReplicaProperties;
import org.danteplanner.backend.shared.config.ReadFallbackConfig;
import org.danteplanner.backend.shared.gtid.GtidCookie;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import({TestConfig.class, CausalHarnessSupport.HarnessDataSourceConfig.class})
@ResourceLock(CausalHarnessSupport.SHARED_HARNESS)
class OregonReadFallbackIT {

    private static final String REPLICATED_TITLE = "oregon-fallback-replicated";
    private static final String PRIMARY_ONLY_TITLE = "oregon-fallback-primary-only";

    private static final int BREAKER_WINDOW = 2;

    private static final long PROBE_DEADLINE_MS = 5_000L;
    private static final long STATE_POLL_INTERVAL_MS = 100L;
    private static final long RELAPSE_WINDOW_MS = 6_000L;
    private static final long READ_INTERVAL_MS = 250L;

    private static final String BREAKER_STATE_GAUGE = "resilience4j.circuitbreaker.state";

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
    private CircuitBreaker primaryReadBreaker;

    @Autowired
    @Qualifier("primaryPool")
    private HikariDataSource primaryPool;

    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;

    @Autowired
    @Qualifier("replicaJdbcTemplate")
    private JdbcTemplate replicaJdbcTemplate;

    private ReplicationControl replicationControl;

    @DynamicPropertySource
    static void oregonProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> CausalHarnessSupport.withSessionStateTracking(primaryUrlThroughProxy()));
        registry.add("spring.datasource.username", CausalHarnessSupport.PRIMARY::getUsername);
        registry.add("spring.datasource.password", CausalHarnessSupport.PRIMARY::getPassword);
        registry.add("spring.flyway.url", CausalHarnessSupport.PRIMARY::getJdbcUrl);
        registry.add("spring.flyway.user", CausalHarnessSupport.PRIMARY::getUsername);
        registry.add("spring.flyway.password", CausalHarnessSupport.PRIMARY::getPassword);
        registry.add("redis.auth.host", CausalHarnessSupport.AUTH_REDIS::getRedisHost);
        registry.add("redis.auth.port", CausalHarnessSupport.AUTH_REDIS::getRedisPort);
        registry.add("redis.auth-local.host", CausalHarnessSupport.AUTH_REDIS::getRedisHost);
        registry.add("redis.auth-local.port", CausalHarnessSupport.AUTH_REDIS::getRedisPort);
        registry.add("redis.rate-limit.host", CausalHarnessSupport.RATE_LIMIT_REDIS::getRedisHost);
        registry.add("redis.rate-limit.port", CausalHarnessSupport.RATE_LIMIT_REDIS::getRedisPort);

        registry.add("datasource.routing.enabled", () -> "true");
        registry.add("datasource.replica.enabled", () -> "false");
        registry.add(FallbackReplicaProperties.URL_PROPERTY, CausalHarnessSupport.REPLICA::getJdbcUrl);
        registry.add("datasource.fallback-replica.breaker.sliding-window-size", () -> BREAKER_WINDOW);
        registry.add("datasource.fallback-replica.breaker.minimum-number-of-calls", () -> BREAKER_WINDOW);
    }

    private static String primaryUrlThroughProxy() {
        String listen = CausalHarnessSupport.APP_TO_PRIMARY_PROXY.getListen();
        int listenPort = Integer.parseInt(listen.substring(listen.lastIndexOf(':') + 1));
        return "jdbc:mysql://" + CausalHarnessSupport.TOXIPROXY.getHost() + ":"
                + CausalHarnessSupport.TOXIPROXY.getMappedPort(listenPort) + "/"
                + CausalHarnessSupport.PRIMARY.getDatabaseName();
    }

    @BeforeEach
    void restoreThePrimaryPath() throws IOException {
        CausalHarnessSupport.APP_TO_PRIMARY_PROXY.enable();
        new ToxiproxyControl(CausalHarnessSupport.APP_TO_PRIMARY_PROXY).removeWan();
        replicationControl = new ReplicationControl(primaryJdbcTemplate, replicaJdbcTemplate);
        replicaJdbcTemplate.execute("START REPLICA");
        primaryReadBreaker.reset();
        assertThat(primaryPool.getJdbcUrl())
                .as("the Oregon primary must be reached through the proxy the test cuts")
                .isEqualTo(CausalHarnessSupport.withSessionStateTracking(primaryUrlThroughProxy()));
    }

    @Test
    void rywCookie_WhenRegionHasNoReplica_WriteMintsCookieAndGatedReadLeavesItUntouched()
            throws Exception {
        Session session = newSession("oregon-ryw-");
        UUID plannerId = UUID.randomUUID();
        double gateRunsBefore = gateRuns();

        try {
            upsert(session, plannerId, REPLICATED_TITLE, false).andExpect(status().is2xxSuccessful());
            replicationControl.awaitCaughtUp();
            replicationControl.stopReplica();
            MvcResult written = upsert(session, plannerId, PRIMARY_ONLY_TITLE, true)
                    .andExpect(status().is2xxSuccessful())
                    .andReturn();
            Cookie rywCookie = rywCookie(written);

            MvcResult read = mockMvc.perform(get("/api/planner/md/" + plannerId)
                            .cookie(session.auth(), session.device(), rywCookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title", is(PRIMARY_ONLY_TITLE)))
                    .andReturn();

            assertThat(read.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                    .as("with no replica the read must neither clear nor re-mint the ryw cookie")
                    .noneMatch(header -> header.startsWith(GtidCookie.NAME + "="));
            assertThat(gateRuns())
                    .as("a region without a replica never runs the read gate")
                    .isEqualTo(gateRunsBefore);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void primaryReadBreaker_WhenPrimaryUnreachableWithoutTraffic_ProbeOpensTheBreaker() throws Exception {
        double openedBefore = transitions("closed", "open");

        try {
            cutThePrimary();
            awaitBreaker(CircuitBreaker.State.OPEN);

            assertThat(transitions("closed", "open")).isGreaterThanOrEqualTo(openedBefore + 1);
            assertThat(breakerStateGauge("open")).isEqualTo(1.0);
        } finally {
            restoreThePrimary();
        }
    }

    @Test
    void primaryReadBreaker_WhenPrimaryReturnsWithoutTraffic_ProbeClosesTheBreaker() throws Exception {
        Session session = newSession("oregon-probe-close-");
        UUID plannerId = UUID.randomUUID();

        try {
            writePrimaryOnlyTitle(session, plannerId);
            cutThePrimary();
            awaitBreaker(CircuitBreaker.State.OPEN);

            CausalHarnessSupport.APP_TO_PRIMARY_PROXY.enable();
            awaitBreaker(CircuitBreaker.State.CLOSED);

            assertThat(readTitle(session, plannerId))
                    .as("a closed breaker sends reads back to the primary")
                    .isEqualTo(PRIMARY_ONLY_TITLE);
        } finally {
            restoreThePrimary();
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void primaryReadBreaker_WhenOpen_ServesStaleReadsAndFailsWritesTyped() throws Exception {
        Session session = newSession("oregon-breaker-");
        UUID plannerId = UUID.randomUUID();

        try {
            writePrimaryOnlyTitle(session, plannerId);
            assertThat(readTitle(session, plannerId))
                    .as("closed breaker: reads go to the primary")
                    .isEqualTo(PRIMARY_ONLY_TITLE);

            cutThePrimary();
            awaitBreaker(CircuitBreaker.State.OPEN);

            assertThat(readTitle(session, plannerId))
                    .as("open breaker: reads are served by the fallback replica, stale by design")
                    .isEqualTo(REPLICATED_TITLE);
            upsert(session, UUID.randomUUID(), "oregon-breaker-blocked-write", false)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("WRITE_TEMPORARILY_UNAVAILABLE"));
        } finally {
            restoreThePrimary();
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void primaryReadBreaker_WhenPrimaryFailsAgainRightAfterRecovery_ReadsStayUp() throws Exception {
        Session session = newSession("oregon-relapse-");
        UUID plannerId = UUID.randomUUID();

        try {
            upsert(session, plannerId, REPLICATED_TITLE, false).andExpect(status().is2xxSuccessful());
            replicationControl.awaitCaughtUp();
            cutThePrimary();
            awaitBreaker(CircuitBreaker.State.OPEN);
            CausalHarnessSupport.APP_TO_PRIMARY_PROXY.enable();
            awaitBreaker(CircuitBreaker.State.CLOSED);

            cutThePrimary();
            long relapseEnd = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RELAPSE_WINDOW_MS);
            while (System.nanoTime() < relapseEnd) {
                mockMvc.perform(get("/api/planner/md/" + plannerId).cookie(session.auth(), session.device()))
                        .andExpect(status().isOk());
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(READ_INTERVAL_MS));
            }
        } finally {
            restoreThePrimary();
        }
    }

    private void writePrimaryOnlyTitle(Session session, UUID plannerId) throws Exception {
        upsert(session, plannerId, REPLICATED_TITLE, false).andExpect(status().is2xxSuccessful());
        replicationControl.awaitCaughtUp();
        replicationControl.stopReplica();
        upsert(session, plannerId, PRIMARY_ONLY_TITLE, true).andExpect(status().is2xxSuccessful());
    }

    private void cutThePrimary() throws IOException {
        CausalHarnessSupport.APP_TO_PRIMARY_PROXY.disable();
        primaryPool.getHikariPoolMXBean().softEvictConnections();
    }

    private void restoreThePrimary() throws IOException {
        CausalHarnessSupport.APP_TO_PRIMARY_PROXY.enable();
        awaitBreaker(CircuitBreaker.State.CLOSED);
    }

    private void awaitBreaker(CircuitBreaker.State expected) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PROBE_DEADLINE_MS);
        while (primaryReadBreaker.getState() != expected && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(STATE_POLL_INTERVAL_MS));
        }
        assertThat(primaryReadBreaker.getState())
                .as("the probe must move the breaker to %s within %d ms", expected, PROBE_DEADLINE_MS)
                .isEqualTo(expected);
    }

    private String readTitle(Session session, UUID plannerId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/planner/md/" + plannerId)
                        .cookie(session.auth(), session.device()))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.title");
    }

    private ResultActions upsert(Session session, UUID plannerId, String title, boolean overwrite)
            throws Exception {
        UpsertPlannerRequest request = new UpsertPlannerRequest(
                plannerId.toString(), "5F", title, PlannerStatus.DRAFT, TestDataFactory.VALID_CONTENT, 7,
                PlannerType.MIRROR_DUNGEON, null, null);
        String path = "/api/planner/md/" + plannerId + (overwrite ? "?force=true" : "");
        return mockMvc.perform(put(path).with(withCsrf())
                .cookie(session.auth(), session.device())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private static Cookie rywCookie(MvcResult written) {
        List<String> setCookies = written.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        String header = setCookies.stream()
                .filter(candidate -> candidate.startsWith(GtidCookie.NAME + "="))
                .findFirst()
                .orElse(null);
        assertThat(header).as("a write in Oregon must mint %s among %s", GtidCookie.NAME, setCookies)
                .isNotNull();
        String value = header.split(";", 2)[0].substring(GtidCookie.NAME.length() + 1);
        assertThat(GtidCookie.decode(value)).as("the minted cookie carries a GTID").isPresent();
        return new Cookie(GtidCookie.NAME, value);
    }

    private Session newSession(String emailPrefix) {
        User user = TestDataFactory.createTestUser(
                userRepository, emailPrefix + UUID.randomUUID() + "@example.com");
        return new Session(
                AuthCookies.accessToken(TestDataFactory.generateAccessToken(jwtTokenService, user)),
                AuthCookies.freshDeviceId());
    }

    private double gateRuns() {
        return meterRegistry.find("gtid.gate").counters().stream().mapToDouble(Counter::count).sum();
    }

    private double transitions(String from, String to) {
        Counter counter = meterRegistry.find(ReadFallbackConfig.BREAKER_TRANSITIONS)
                .tag("from", from).tag("to", to).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private double breakerStateGauge(String state) {
        Gauge gauge = meterRegistry.find(BREAKER_STATE_GAUGE)
                .tag("name", ReadFallbackConfig.PRIMARY_READ_BREAKER).tag("state", state).gauge();
        return gauge == null ? Double.NaN : gauge.value();
    }

    private record Session(Cookie auth, Cookie device) {
    }
}

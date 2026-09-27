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
import org.junit.jupiter.api.DisplayName;
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
    private static final String WAIT_IN_OPEN_STATE = "PT1S";

    private static final long RECOVERY_DEADLINE_MS = 60_000L;
    private static final long RECOVERY_POLL_INTERVAL_MS = 250L;

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
        registry.add("datasource.fallback-replica.breaker.wait-duration-in-open-state", () -> WAIT_IN_OPEN_STATE);
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
    @DisplayName("Oregon without a replica: a write mints ryw_gtid, and a read carrying it is served from the primary without running the gate or touching the cookie")
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
    @DisplayName("Primary unreachable: past the breaker threshold reads serve from the fallback replica and writes fail typed; once the primary returns, half-open probes move reads back")
    void primaryReadBreaker_WhenPrimaryUnreachable_ReadsFallBackToReplicaUntilProbesSucceed()
            throws Exception {
        Session session = newSession("oregon-breaker-");
        UUID plannerId = UUID.randomUUID();

        try {
            upsert(session, plannerId, REPLICATED_TITLE, false).andExpect(status().is2xxSuccessful());
            replicationControl.awaitCaughtUp();
            replicationControl.stopReplica();
            upsert(session, plannerId, PRIMARY_ONLY_TITLE, true).andExpect(status().is2xxSuccessful());
            assertThat(readTitle(session, plannerId))
                    .as("closed breaker: reads go to the primary")
                    .isEqualTo(PRIMARY_ONLY_TITLE);

            CausalHarnessSupport.APP_TO_PRIMARY_PROXY.disable();
            primaryPool.getHikariPoolMXBean().softEvictConnections();

            for (int attempt = 0; attempt < BREAKER_WINDOW
                    && primaryReadBreaker.getState() == CircuitBreaker.State.CLOSED; attempt++) {
                mockMvc.perform(get("/api/planner/md/" + plannerId).cookie(session.auth(), session.device()))
                        .andExpect(status().isServiceUnavailable());
            }
            assertThat(primaryReadBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(breakerStateGauge("open")).isEqualTo(1.0);
            assertThat(transitions("closed", "open")).isGreaterThanOrEqualTo(1.0);

            assertThat(readTitle(session, plannerId))
                    .as("open breaker: reads are served by the fallback replica, stale by design")
                    .isEqualTo(REPLICATED_TITLE);

            upsert(session, UUID.randomUUID(), "oregon-breaker-blocked-write", false)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("WRITE_TEMPORARILY_UNAVAILABLE"));

            CausalHarnessSupport.APP_TO_PRIMARY_PROXY.enable();
            awaitReadsBackOnThePrimary(session, plannerId);

            assertThat(transitions("open", "half_open")).isGreaterThanOrEqualTo(1.0);
            assertThat(transitions("half_open", "closed")).isGreaterThanOrEqualTo(1.0);
        } finally {
            CausalHarnessSupport.APP_TO_PRIMARY_PROXY.enable();
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    private void awaitReadsBackOnThePrimary(Session session, UUID plannerId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RECOVERY_DEADLINE_MS);
        String title = readTitle(session, plannerId);
        while ((!PRIMARY_ONLY_TITLE.equals(title)
                || primaryReadBreaker.getState() != CircuitBreaker.State.CLOSED)
                && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(RECOVERY_POLL_INTERVAL_MS));
            title = readTitle(session, plannerId);
        }
        assertThat(title)
                .as("after the primary returns, a half-open probe must move reads back to it")
                .isEqualTo(PRIMARY_ONLY_TITLE);
        assertThat(primaryReadBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
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

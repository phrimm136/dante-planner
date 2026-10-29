package org.danteplanner.backend.integration;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import tools.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.config.ReadSourcesConfig;
import org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource;
import org.danteplanner.backend.shared.config.RoutingKey;
import org.danteplanner.backend.shared.config.UndeclaredPrimaryAccessGuard;
import org.danteplanner.backend.shared.gtid.GtidCookie;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.UpsertRequests;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

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
@Import(TestConfig.class)
class TwoRegionHarnessIT extends ReplicaRoutingHarnessSupport {

    private static final String REPLICATED_TITLE = "two-region-replicated";
    private static final String PRIMARY_ONLY_TITLE = "two-region-primary-only";

    private static final long PROBE_DEADLINE_MS = 5_000L;
    private static final long STATE_POLL_INTERVAL_MS = 100L;

    private static final String BREAKER_STATE_GAUGE = "resilience4j.circuitbreaker.state";
    private static final String POOL_ACQUIRE_TIMER = "hikaricp.connections.acquire";

    private static OregonContext oregon;

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
    private DataSource dataSource;

    @Autowired
    private ReadSourceSelector readSourceSelector;

    @AfterAll
    static void closeOregon() {
        if (oregon != null) {
            oregon.close();
        }
    }

    @Test
    void oregonWrite_WhenAuthorReadsInSeoulWithTheMintedCookie_ReturnsTheWrittenSyncVersion() throws Exception {
        Session session = newSession("two-region-ryw-");
        UUID plannerId = UUID.randomUUID();

        try {
            MvcResult written = writePrimaryOnlyTitle(session, plannerId);
            long writtenVersion = syncVersion(written);
            double pinsBefore = seoulGatePins();

            mockMvc.perform(get(plannerPath(plannerId))
                            .cookie(session.auth(), session.device(), rywCookie(written)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title", is(PRIMARY_ONLY_TITLE)))
                    .andExpect(jsonPath("$.syncVersion").value(writtenVersion));
            assertThat(seoulGatePins())
                    .as("the Seoul gate must pin the primary for a cookie its stopped replica has not applied")
                    .isEqualTo(pinsBefore + 1);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void oregonPrimaryCut_WhenBothRegionsWrite_OnlyOregonAnswersWriteUnavailable() throws Exception {
        Session session = newSession("two-region-cut-write-");
        MockMvc oregonMvc = oregon().mockMvc();

        try {
            toxiproxyControl.cutOregonPrimary();
            awaitOregonBreaker(CircuitBreaker.State.OPEN);

            upsert(oregonMvc, session, UUID.randomUUID(), "two-region-oregon-blocked", false)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("WRITE_TEMPORARILY_UNAVAILABLE"));
            upsert(mockMvc, session, UUID.randomUUID(), "two-region-seoul-write", false)
                    .andExpect(status().isCreated());
        } finally {
            restoreOregonPrimary();
        }
    }

    @Test
    void oregonOwnerRead_WhenPrimaryCut_IsServedThroughTheFallbackReplicaPool() throws Exception {
        Session session = newSession("two-region-cut-read-");
        UUID plannerId = UUID.randomUUID();
        upsert(oregon().mockMvc(), session, plannerId, REPLICATED_TITLE, false).andExpect(status().isCreated());
        replicationControl.awaitCaughtUp();

        try {
            toxiproxyControl.cutOregonPrimary();
            awaitOregonBreaker(CircuitBreaker.State.OPEN);
            long acquisitionsBefore = fallbackAcquisitions();

            oregon().mockMvc().perform(get(plannerPath(plannerId)).cookie(session.auth(), session.device()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title", is(REPLICATED_TITLE)));

            assertThat(fallbackAcquisitions())
                    .as("the read must acquire a connection from the Oregon %s pool",
                            OregonContext.REPLICA_SOURCE)
                    .isGreaterThanOrEqualTo(acquisitionsBefore + 1);
        } finally {
            restoreOregonPrimary();
        }
    }

    @Test
    void oregonPrimaryRestore_WhenProbeSucceeds_OregonReadsThePrimaryAgain() throws Exception {
        Session session = newSession("two-region-restore-");
        UUID plannerId = UUID.randomUUID();

        try {
            upsert(oregon().mockMvc(), session, plannerId, REPLICATED_TITLE, false).andExpect(status().isCreated());
            replicationControl.awaitCaughtUp();
            toxiproxyControl.cutOregonPrimary();
            awaitOregonBreaker(CircuitBreaker.State.OPEN);
            assertThat(oregonTitle(session, plannerId))
                    .as("an open breaker serves a planner unchanged since before the cut from the replica")
                    .isEqualTo(REPLICATED_TITLE);

            replicationControl.stopReplica();
            upsert(mockMvc, session, plannerId, PRIMARY_ONLY_TITLE, true).andExpect(status().isOk());

            long restoredAt = System.nanoTime();
            toxiproxyControl.restoreOregonPrimary();
            long deadline = restoredAt + TimeUnit.MILLISECONDS.toNanos(PROBE_DEADLINE_MS);
            String title = oregonTitle(session, plannerId);
            while (!PRIMARY_ONLY_TITLE.equals(title) && System.nanoTime() < deadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(STATE_POLL_INTERVAL_MS));
                title = oregonTitle(session, plannerId);
            }
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - restoredAt);
            System.out.println("two-region restore: Oregon read the primary " + elapsedMs + " ms after restore");

            assertThat(title)
                    .as("within %d ms of the restore the probe closes the breaker and reads reach the primary",
                            PROBE_DEADLINE_MS)
                    .isEqualTo(PRIMARY_ONLY_TITLE);
        } finally {
            restoreOregonPrimary();
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void routingOverride_WhenSeoulPinnedThePrimaryOnThisThread_IsAbsentAfterTheNextOregonRequest() throws Exception {
        ReadOnlyRoutingDataSource.pinTo(RoutingKey.PRIMARY);
        try {
            assertThat(readOnlyRoutingKey())
                    .as("the probe must see an override when one is set")
                    .isEqualTo(RoutingKey.PRIMARY);
        } finally {
            ReadOnlyRoutingDataSource.clear();
        }
        Session session = newSession("two-region-override-");
        UUID plannerId = UUID.randomUUID();

        try {
            MvcResult written = writePrimaryOnlyTitle(session, plannerId);
            double pinsBefore = seoulGatePins();
            mockMvc.perform(get(plannerPath(plannerId))
                            .cookie(session.auth(), session.device(), rywCookie(written)))
                    .andExpect(status().isOk());
            assertThat(seoulGatePins()).as("the Seoul read pinned the primary").isEqualTo(pinsBefore + 1);

            oregon().mockMvc().perform(get(plannerPath(plannerId)).cookie(session.auth(), session.device()))
                    .andExpect(status().isOk());

            assertThat(readOnlyRoutingKey())
                    .as("no routing override survives from the Seoul request into the Oregon one")
                    .isEqualTo(RoutingKey.REPLICA);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void seoulPrimaryBreaker_WhenOpen_LeavesTheOregonPrimaryBreakerClosed() {
        double oregonOpenedBefore = oregonTransitions("closed", "open");
        CircuitBreaker seoulPrimary = readSourceSelector.authority().breaker();

        try {
            seoulPrimary.transitionToOpenState();

            assertThat(oregonBreakerGauge("closed"))
                    .as("the Oregon breaker for the primary stays closed while Seoul's is open")
                    .isEqualTo(1.0);
            assertThat(oregonTransitions("closed", "open"))
                    .as("opening Seoul's breaker records no transition in Oregon")
                    .isEqualTo(oregonOpenedBefore);
            assertThat(seoulPrimary.getState())
                    .as("Seoul's breaker was open throughout the Oregon checks")
                    .isEqualTo(CircuitBreaker.State.OPEN);
        } finally {
            seoulPrimary.reset();
        }
    }

    private double oregonTransitions(String from, String to) {
        Counter counter = oregon().meterRegistry().find(ReadSourcesConfig.BREAKER_TRANSITIONS)
                .tag("source", ReadSourceProperties.PRIMARY).tag("from", from).tag("to", to).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static OregonContext oregon() {
        if (oregon == null) {
            oregon = OregonContext.start();
        }
        return oregon;
    }

    private MvcResult writePrimaryOnlyTitle(Session session, UUID plannerId) throws Exception {
        upsert(oregon().mockMvc(), session, plannerId, REPLICATED_TITLE, false).andExpect(status().isCreated());
        replicationControl.awaitCaughtUp();
        replicationControl.stopReplica();
        return upsert(oregon().mockMvc(), session, plannerId, PRIMARY_ONLY_TITLE, true)
                .andExpect(status().isOk())
                .andReturn();
    }

    private void restoreOregonPrimary() throws IOException {
        toxiproxyControl.restoreOregonPrimary();
        awaitOregonBreaker(CircuitBreaker.State.CLOSED);
    }

    private void awaitOregonBreaker(CircuitBreaker.State expected) {
        String state = expected.name().toLowerCase(Locale.ROOT);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PROBE_DEADLINE_MS);
        while (oregonBreakerGauge(state) != 1.0 && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(STATE_POLL_INTERVAL_MS));
        }
        assertThat(oregonBreakerGauge(state))
                .as("the Oregon probe must move the breaker to %s within %d ms", expected, PROBE_DEADLINE_MS)
                .isEqualTo(1.0);
    }

    private double oregonBreakerGauge(String state) {
        Gauge gauge = oregon().meterRegistry().find(BREAKER_STATE_GAUGE)
                .tag("name", ReadSourcesConfig.breakerName(ReadSourceProperties.PRIMARY)).tag("state", state).gauge();
        return gauge == null ? Double.NaN : gauge.value();
    }

    private long fallbackAcquisitions() {
        Timer timer = oregon().meterRegistry().find(POOL_ACQUIRE_TIMER)
                .tag("pool", OregonContext.REPLICA_SOURCE).timer();
        assertThat(timer).as("the Oregon context meters the %s pool", OregonContext.REPLICA_SOURCE)
                .isNotNull();
        return timer.count();
    }

    private double seoulGatePins() {
        Counter counter = meterRegistry.find("gtid.gate").tag("outcome", "primary").counter();
        return counter == null ? 0.0 : counter.count();
    }

    private RoutingKey readOnlyRoutingKey() {
        ReadOnlyRoutingDataSource probe = new ReadOnlyRoutingDataSource(
                new UndeclaredPrimaryAccessGuard(new SimpleMeterRegistry(), true));
        TransactionTemplate readOnly = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        readOnly.setReadOnly(true);
        return readOnly.execute(status -> (RoutingKey) probe.determineCurrentLookupKey());
    }

    private String oregonTitle(Session session, UUID plannerId) throws Exception {
        MvcResult result = oregon().mockMvc().perform(get(plannerPath(plannerId))
                        .cookie(session.auth(), session.device()))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.title");
    }

    private ResultActions upsert(MockMvc target, Session session, UUID plannerId, String title, boolean overwrite)
            throws Exception {
        UpsertPlannerRequest request = UpsertRequests.draft().id(plannerId.toString()).title(title).build();
        String path = plannerPath(plannerId) + (overwrite ? "?force=true" : "");
        return target.perform(put(path).with(withCsrf())
                .cookie(session.auth(), session.device())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private static String plannerPath(UUID plannerId) {
        return "/api/planner/md/" + plannerId;
    }

    private static long syncVersion(MvcResult result) throws Exception {
        Number version = JsonPath.read(result.getResponse().getContentAsString(), "$.syncVersion");
        return version.longValue();
    }

    private static Cookie rywCookie(MvcResult written) {
        List<String> setCookies = written.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        String header = setCookies.stream()
                .filter(candidate -> candidate.startsWith(GtidCookie.NAME + "="))
                .findFirst()
                .orElse(null);
        assertThat(header).as("an Oregon write must mint %s among %s", GtidCookie.NAME, setCookies)
                .isNotNull();
        return new Cookie(GtidCookie.NAME, header.split(";", 2)[0].substring(GtidCookie.NAME.length() + 1));
    }

    private Session newSession(String emailPrefix) {
        User user = TestDataFactory.createTestUser(
                userRepository, emailPrefix + UUID.randomUUID() + "@example.com");
        return new Session(
                AuthCookies.accessToken(TestDataFactory.generateAccessToken(jwtTokenService, user)),
                AuthCookies.freshDeviceId());
    }

    private record Session(Cookie auth, Cookie device) {
    }
}

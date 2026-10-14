package org.danteplanner.backend.integration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.autoconfigure.web.servlet.SpringBootMockMvcBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.Toxic;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.BackendApplication;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.config.ReadSourcesConfig;
import org.danteplanner.backend.shared.gtid.GtidCookie;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.UpsertRequests;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

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
class ReadSourceOutageIT extends ReplicaRoutingHarnessSupport {

    private static final String READ_UNAVAILABLE = "READ_TEMPORARILY_UNAVAILABLE";
    private static final String WRITE_UNAVAILABLE = "WRITE_TEMPORARILY_UNAVAILABLE";
    private static final String RETRY_AFTER_SECONDS = "10";

    private static final String REPLICATED_TITLE = "read-source-replicated";
    private static final String PRIMARY_ONLY_TITLE = "read-source-primary-only";

    private static final String PLANNER_PATH = "/api/planner/md/";

    private static final String SEOUL_TO_REPLICA_PROXY_NAME = "seoul-to-replica";
    private static final int SEOUL_TO_REPLICA_LISTEN_PORT = 8669;
    private static final int MYSQL_INTERNAL_PORT = 3306;

    private static final String BREAKER_STATE_GAUGE = "resilience4j.circuitbreaker.state";
    private static final long PROBE_DEADLINE_MS = 5_000L;
    private static final long STATE_POLL_INTERVAL_MS = 100L;
    private static final long RECOVERY_DEADLINE_MS = 10_000L;

    private static final Proxy SEOUL_TO_REPLICA_PROXY = createSeoulToReplicaProxy();

    private static OregonContext oregon;

    private static ConfigurableApplicationContext proxiedSeoul;

    private static MockMvc proxiedSeoulMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    @Qualifier("replicaJdbcTemplate")
    private JdbcTemplate replicaJdbcTemplate;

    @AfterAll
    static void closeContexts() {
        if (oregon != null) {
            oregon.close();
        }
        if (proxiedSeoul != null) {
            proxiedSeoul.close();
        }
    }

    @Test
    void oregonOwnerRead_WhenPrimaryCutAndReplicaStillHoldsTheDeletedPlanner_Returns404() throws Exception {
        Session session = newSession("read-source-deleted-");
        UUID plannerId = UUID.randomUUID();

        try {
            assertThat(upsert(oregon().mockMvc(), session, plannerId, REPLICATED_TITLE, false).getResponse()
                    .getStatus()).isEqualTo(201);
            replicationControl.awaitCaughtUp();
            replicationControl.stopReplica();
            assertThat(perform(oregon().mockMvc(), delete(plannerPath(plannerId)).with(withCsrf())
                    .cookie(session.auth(), session.device())).getResponse().getStatus()).isEqualTo(204);
            assertThat(replicaLiveContentRows(plannerId))
                    .as("the stopped replica must still hold the planner undeleted").isEqualTo(1);

            cutOregonPrimary();
            MvcResult read = perform(oregon().mockMvc(),
                    get(plannerPath(plannerId)).cookie(session.auth(), session.device()));

            assertThat(observed(read)).as("Oregon read of a deleted planner during the primary cut")
                    .startsWith("404 ");
        } finally {
            restoreOregonPrimary();
            resumeReplication();
        }
    }

    @Test
    void oregonOwnerRead_WhenPrimaryCutAndReplicaMissesThePlanner_ReturnsReadUnavailable() throws Exception {
        Session session = newSession("read-source-miss-");
        UUID plannerId = UUID.randomUUID();

        try {
            replicationControl.stopReplica();
            assertThat(upsert(oregon().mockMvc(), session, plannerId, PRIMARY_ONLY_TITLE, false).getResponse()
                    .getStatus()).isEqualTo(201);
            assertThat(replicaPlannerRows(plannerId))
                    .as("the stopped replica must not hold the planner").isZero();

            cutOregonPrimary();
            MvcResult read = perform(oregon().mockMvc(),
                    get(plannerPath(plannerId)).cookie(session.auth(), session.device()));

            assertThat(observed(read)).as("Oregon read of a planner only the cut primary holds")
                    .isEqualTo(unavailable(READ_UNAVAILABLE));
        } finally {
            restoreOregonPrimary();
            resumeReplication();
        }
    }

    @Test
    void oregonPublishedRead_WhenPrimaryCutAndReplicaMissesTheWithdrawal_ReturnsReadUnavailable() throws Exception {
        Session session = newSession("read-source-withdrawn-");
        UUID plannerId = UUID.randomUUID();

        try {
            assertThat(perform(oregon().mockMvc(), post(plannerPath(plannerId) + "/publish").with(withCsrf())
                    .cookie(session.auth(), session.device())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(publishRequest(session, plannerId))))
                    .getResponse().getStatus()).isEqualTo(200);
            replicationControl.awaitCaughtUp();
            replicationControl.stopReplica();
            assertThat(perform(oregon().mockMvc(), post(plannerPath(plannerId) + "/unpublish").with(withCsrf())
                    .cookie(session.auth(), session.device())).getResponse().getStatus()).isEqualTo(200);
            assertThat(replicaPublishedRows(plannerId))
                    .as("the stopped replica must still show the planner published").isEqualTo(1);

            cutOregonPrimary();
            MvcResult read = perform(oregon().mockMvc(), get(PLANNER_PATH + "published/" + plannerId));

            assertThat(observed(read)).as("anonymous Oregon read of a withdrawn planner during the primary cut")
                    .isEqualTo(unavailable(READ_UNAVAILABLE));
        } finally {
            restoreOregonPrimary();
            resumeReplication();
        }
    }

    @Test
    void oregonAuthorRead_WhenPrimaryCutAndReplicaBehindTheCookie_ReturnsReadUnavailableUntilTheReplicaApplies()
            throws Exception {
        Session session = newSession("read-source-cookie-");
        UUID plannerId = UUID.randomUUID();

        try {
            replicationControl.stopReplica();
            MvcResult saved = upsert(oregon().mockMvc(), session, plannerId, PRIMARY_ONLY_TITLE, false);
            assertThat(saved.getResponse().getStatus()).isEqualTo(201);
            Cookie rywCookie = rywCookie(saved);
            assertThat(replicaPlannerRows(plannerId))
                    .as("the stopped replica must not hold the author's save").isZero();

            cutOregonPrimary();
            MvcResult behind = perform(oregon().mockMvc(),
                    get(plannerPath(plannerId)).cookie(session.auth(), session.device(), rywCookie));

            assertThat(observed(behind)).as("Oregon author read while the replica lacks the save")
                    .isEqualTo(unavailable(READ_UNAVAILABLE));
            assertThat(rywSetCookies(behind)).as("the unavailable read keeps the author's cookie").isEmpty();

            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
            MvcResult caughtUp = perform(oregon().mockMvc(),
                    get(plannerPath(plannerId)).cookie(session.auth(), session.device(), rywCookie));

            assertThat(observed(caughtUp)).as("Oregon author read once the replica applied the save")
                    .isEqualTo("200 title=" + PRIMARY_ONLY_TITLE);
            assertThat(rywSetCookies(caughtUp)).as("the caught-up read clears the author's cookie")
                    .anyMatch(header -> header.startsWith(GtidCookie.NAME + "=;") && header.contains("Max-Age=0"));
        } finally {
            restoreOregonPrimary();
            resumeReplication();
        }
    }

    @Test
    void oregonAuthorRead_WhenPrimaryCutWithItsBreakerClosedAndReplicaBehindTheCookie_ReturnsReadUnavailable()
            throws Exception {
        Session session = newSession("read-source-cookie-closed-");
        UUID plannerId = UUID.randomUUID();
        CountDownLatch probeHeld = new CountDownLatch(1);
        CountDownLatch releaseProbe = new CountDownLatch(1);

        try {
            assertThat(upsert(oregon().mockMvc(), session, plannerId, REPLICATED_TITLE, false).getResponse()
                    .getStatus()).isEqualTo(201);
            replicationControl.awaitCaughtUp();
            replicationControl.stopReplica();
            MvcResult saved = upsert(oregon().mockMvc(), session, plannerId, PRIMARY_ONLY_TITLE, true);
            assertThat(saved.getResponse().getStatus()).isEqualTo(200);
            Cookie rywCookie = rywCookie(saved);
            assertThat(replicaTitle(plannerId))
                    .as("the stopped replica must hold the title from before the save").isEqualTo(REPLICATED_TITLE);

            holdOregonProbe(probeHeld, releaseProbe);
            cutOregonPrimaryWithoutProbe();
            MvcResult read = perform(oregon().mockMvc(),
                    get(plannerPath(plannerId)).cookie(session.auth(), session.device(), rywCookie));

            assertThat(observed(read)).as("Oregon author read while the closed-breaker primary is cut")
                    .isEqualTo(unavailable(READ_UNAVAILABLE));
            assertThat(rywSetCookies(read)).as("the unavailable read keeps the author's cookie").isEmpty();
        } finally {
            releaseProbe.countDown();
            restoreOregonPrimary();
            resumeReplication();
        }
    }

    @Test
    void seoulOwnerRead_WhenReplicaUnreachable_ReturnsThePrimaryContent() throws Exception {
        Session session = newSession("read-source-seoul-replica-");
        UUID plannerId = UUID.randomUUID();

        try {
            restoreSeoulPaths();
            assertThat(upsert(proxiedSeoul(), session, plannerId, REPLICATED_TITLE, false).getResponse().getStatus())
                    .isEqualTo(201);
            replicationControl.awaitCaughtUp();
            replicationControl.stopReplica();
            assertThat(upsert(proxiedSeoul(), session, plannerId, PRIMARY_ONLY_TITLE, true).getResponse().getStatus())
                    .isEqualTo(200);
            assertThat(replicaTitle(plannerId))
                    .as("the stopped replica must hold the older title").isEqualTo(REPLICATED_TITLE);

            cutSeoulPaths(SEOUL_TO_REPLICA_PROXY);
            MvcResult read = perform(proxiedSeoul(),
                    get(plannerPath(plannerId)).cookie(session.auth(), session.device()));

            assertThat(observed(read)).as("Seoul read with its replica unreachable")
                    .isEqualTo("200 title=" + PRIMARY_ONLY_TITLE);
        } finally {
            restoreSeoulPaths();
            resumeReplication();
        }
    }

    @Test
    void seoulRequests_WhenEverySourceUnreachable_ReadAnswersReadUnavailableAndWriteAnswersWriteUnavailable()
            throws Exception {
        Session session = newSession("read-source-all-cut-");
        UUID plannerId = UUID.randomUUID();

        try {
            restoreSeoulPaths();
            assertThat(upsert(proxiedSeoul(), session, plannerId, REPLICATED_TITLE, false).getResponse().getStatus())
                    .isEqualTo(201);
            replicationControl.awaitCaughtUp();

            cutSeoulPaths(SEOUL_TO_REPLICA_PROXY, CausalHarnessSupport.APP_TO_PRIMARY_PROXY);
            MvcResult read = perform(proxiedSeoul(),
                    get(plannerPath(plannerId)).cookie(session.auth(), session.device()));
            MvcResult write = upsert(proxiedSeoul(), session, plannerId, PRIMARY_ONLY_TITLE, true);

            assertThat(observed(read)).as("Seoul read with every source unreachable")
                    .isEqualTo(unavailable(READ_UNAVAILABLE));
            assertThat(observed(write)).as("Seoul write with every source unreachable")
                    .isEqualTo(unavailable(WRITE_UNAVAILABLE));
        } finally {
            restoreSeoulPaths();
            resumeReplication();
        }
    }

    private static OregonContext oregon() {
        if (oregon == null) {
            oregon = OregonContext.start();
        }
        return oregon;
    }

    private void cutOregonPrimary() throws IOException {
        assertThat(anyBreakerOpen(oregon().meterRegistry()))
                .as("no Oregon breaker may be open before the cut").isFalse();
        toxiproxyControl.cutOregonPrimary();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PROBE_DEADLINE_MS);
        while (!anyBreakerOpen(oregon().meterRegistry()) && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(STATE_POLL_INTERVAL_MS));
        }
        assertThat(anyBreakerOpen(oregon().meterRegistry()))
                .as("the Oregon probe must open a breaker within %d ms of the cut", PROBE_DEADLINE_MS).isTrue();
    }

    private static void holdOregonProbe(CountDownLatch held, CountDownLatch release) throws InterruptedException {
        ApplicationContext context = oregon().mockMvc().getDispatcherServlet().getWebApplicationContext();
        context.getBean(ReadSourcesConfig.PROBE_SCHEDULER, ThreadPoolTaskScheduler.class).execute(() -> {
            held.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(held.await(PROBE_DEADLINE_MS, TimeUnit.MILLISECONDS))
                .as("the Oregon probe thread must be held within %d ms", PROBE_DEADLINE_MS).isTrue();
    }

    private void cutOregonPrimaryWithoutProbe() throws IOException {
        toxiproxyControl.cutOregonPrimary();
        ApplicationContext context = oregon().mockMvc().getDispatcherServlet().getWebApplicationContext();
        context.getBeansOfType(HikariDataSource.class).values()
                .forEach(pool -> pool.getHikariPoolMXBean().softEvictConnections());
        assertThat(context.getBean(ReadSourceSelector.class).authority().breaker().getState())
                .as("the Oregon primary breaker must stay closed through the cut").isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private void restoreOregonPrimary() throws IOException {
        toxiproxyControl.restoreOregonPrimary();
        if (oregon != null) {
            awaitRecovered("Oregon", oregon.mockMvc());
        }
    }

    private static MockMvc proxiedSeoul() {
        if (proxiedSeoul == null) {
            proxiedSeoul = new SpringApplicationBuilder(BackendApplication.class, TestConfig.class)
                    .web(WebApplicationType.SERVLET)
                    .profiles("it")
                    .run(proxiedSeoulArguments());
            WebApplicationContext web = (WebApplicationContext) proxiedSeoul;
            DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(web);
            SpringBootMockMvcBuilderCustomizer customizer = new SpringBootMockMvcBuilderCustomizer(web);
            customizer.setPrint(MockMvcPrint.NONE);
            customizer.customize(builder);
            proxiedSeoulMvc = builder.build();
        }
        return proxiedSeoulMvc;
    }

    private static String[] proxiedSeoulArguments() {
        String primaryUrl = urlThroughProxy(CausalHarnessSupport.APP_TO_PRIMARY_PROXY);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("server.port", 0);
        properties.put("spring.datasource.url", CausalHarnessSupport.withSessionStateTracking(primaryUrl));
        properties.put("spring.datasource.username", CausalHarnessSupport.PRIMARY.getUsername());
        properties.put("spring.datasource.password", CausalHarnessSupport.PRIMARY.getPassword());
        properties.put("spring.datasource.hikari.connection-timeout", OregonContext.CONNECTION_TIMEOUT_BUDGET_MS);
        properties.put("spring.flyway.enabled", false);
        properties.put("spring.flyway.url", primaryUrl);
        properties.put("spring.flyway.user", CausalHarnessSupport.PRIMARY.getUsername());
        properties.put("spring.flyway.password", CausalHarnessSupport.PRIMARY.getPassword());
        properties.put("redis.auth.host", CausalHarnessSupport.AUTH_REDIS.getRedisHost());
        properties.put("redis.auth.port", CausalHarnessSupport.AUTH_REDIS.getRedisPort());
        properties.put("redis.auth-local.host", CausalHarnessSupport.AUTH_REDIS.getRedisHost());
        properties.put("redis.auth-local.port", CausalHarnessSupport.AUTH_REDIS.getRedisPort());
        properties.put("redis.rate-limit.host", CausalHarnessSupport.RATE_LIMIT_REDIS.getRedisHost());
        properties.put("redis.rate-limit.port", CausalHarnessSupport.RATE_LIMIT_REDIS.getRedisPort());
        properties.put("datasource.routing.enabled", true);
        properties.put("datasource.read-sources[0].name", OregonContext.REPLICA_SOURCE);
        properties.put("datasource.read-sources[0].url", urlThroughProxy(SEOUL_TO_REPLICA_PROXY));
        properties.put("datasource.read-sources[0].username", CausalHarnessSupport.REPLICA.getUsername());
        properties.put("datasource.read-sources[0].password", CausalHarnessSupport.REPLICA.getPassword());
        properties.put("datasource.read-sources[1].name", "primary");
        return properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }

    private void cutSeoulPaths(Proxy... paths) throws IOException {
        for (Proxy path : paths) {
            path.disable();
        }
        proxiedSeoul.getBeansOfType(HikariDataSource.class).values()
                .forEach(pool -> pool.getHikariPoolMXBean().softEvictConnections());
    }

    private void restoreSeoulPaths() throws IOException {
        SEOUL_TO_REPLICA_PROXY.enable();
        removeToxics(SEOUL_TO_REPLICA_PROXY);
        CausalHarnessSupport.APP_TO_PRIMARY_PROXY.enable();
        if (proxiedSeoulMvc != null) {
            awaitRecovered("proxied Seoul", proxiedSeoulMvc);
        }
    }

    private static void awaitRecovered(String region, MockMvc target) {
        ApplicationContext context = target.getDispatcherServlet().getWebApplicationContext();
        ReadSourceSelector selector = context.getBean(ReadSourceSelector.class);
        HikariDataSource primaryPool = context.getBean("primaryPool", HikariDataSource.class);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RECOVERY_DEADLINE_MS);
        while (!(allBreakersClosed(selector) && primaryConnects(primaryPool)) && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(STATE_POLL_INTERVAL_MS));
        }
        assertThat(selector.sources()).extracting(source -> source.breaker().getState())
                .as("every %s read-source breaker closes within %d ms of the restore", region, RECOVERY_DEADLINE_MS)
                .containsOnly(CircuitBreaker.State.CLOSED);
        assertThat(primaryConnects(primaryPool))
                .as("the %s primary pool lends a connection within %d ms of the restore", region,
                        RECOVERY_DEADLINE_MS)
                .isTrue();
    }

    private static boolean allBreakersClosed(ReadSourceSelector selector) {
        return selector.sources().stream()
                .allMatch(source -> source.breaker().getState() == CircuitBreaker.State.CLOSED);
    }

    private static boolean primaryConnects(HikariDataSource primaryPool) {
        try {
            primaryPool.getConnection().close();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private void resumeReplication() {
        replicationControl.startReplica();
        replicationControl.awaitCaughtUp();
    }

    private static boolean anyBreakerOpen(MeterRegistry registry) {
        return registry.find(BREAKER_STATE_GAUGE).tag("state", "open").gauges().stream()
                .mapToDouble(Gauge::value)
                .anyMatch(value -> value == 1.0);
    }

    private String observed(MvcResult result) throws Exception {
        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString();
        JsonNode json = body.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(body);
        if (status == 200) {
            return status + " title=" + json.path("title").asText();
        }
        return status + " code=" + json.path("code").asText()
                + " retryAfter=" + result.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
    }

    private static String unavailable(String code) {
        return "503 code=" + code + " retryAfter=" + RETRY_AFTER_SECONDS;
    }

    private int replicaPlannerRows(UUID plannerId) {
        return replicaJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM planner WHERE id = UUID_TO_BIN(?)", Integer.class, plannerId.toString());
    }

    private int replicaLiveContentRows(UUID plannerId) {
        return replicaJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM planner_content WHERE planner_id = UUID_TO_BIN(?) AND deleted_at IS NULL",
                Integer.class, plannerId.toString());
    }

    private int replicaPublishedRows(UUID plannerId) {
        return replicaJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM planner_publication WHERE planner_id = UUID_TO_BIN(?) AND published = TRUE",
                Integer.class, plannerId.toString());
    }

    private String replicaTitle(UUID plannerId) {
        return replicaJdbcTemplate.queryForObject(
                "SELECT title FROM planner_content WHERE planner_id = UUID_TO_BIN(?)",
                String.class, plannerId.toString());
    }

    private MvcResult upsert(MockMvc target, Session session, UUID plannerId, String title, boolean overwrite)
            throws Exception {
        UpsertPlannerRequest request = UpsertRequests.draft().id(plannerId.toString()).title(title).build();
        String path = plannerPath(plannerId) + (overwrite ? "?force=true" : "");
        return perform(target, put(path).with(withCsrf())
                .cookie(session.auth(), session.device())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private static UpsertPlannerRequest publishRequest(Session session, UUID plannerId) {
        return UpsertRequests.draft()
                .id(plannerId.toString())
                .title(REPLICATED_TITLE)
                .status(PlannerStatus.SAVED)
                .content(TestDataFactory.planner(session.user()).build().getContentJson())
                .build();
    }

    private static MvcResult perform(MockMvc target, MockHttpServletRequestBuilder request) throws Exception {
        return target.perform(request).andReturn();
    }

    private static String plannerPath(UUID plannerId) {
        return PLANNER_PATH + plannerId;
    }

    private static List<String> rywSetCookies(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(GtidCookie.NAME + "="))
                .toList();
    }

    private static Cookie rywCookie(MvcResult written) {
        String header = rywSetCookies(written).stream().findFirst().orElse(null);
        assertThat(header).as("the save must mint %s", GtidCookie.NAME).isNotNull();
        return new Cookie(GtidCookie.NAME, header.split(";", 2)[0].substring(GtidCookie.NAME.length() + 1));
    }

    private Session newSession(String emailPrefix) {
        User user = TestDataFactory.createTestUser(
                userRepository, emailPrefix + UUID.randomUUID() + "@example.com");
        return new Session(user,
                AuthCookies.accessToken(TestDataFactory.generateAccessToken(jwtTokenService, user)),
                AuthCookies.freshDeviceId());
    }

    private static String urlThroughProxy(Proxy proxy) {
        String listen = proxy.getListen();
        int listenPort = Integer.parseInt(listen.substring(listen.lastIndexOf(':') + 1));
        return "jdbc:mysql://" + CausalHarnessSupport.TOXIPROXY.getHost() + ":"
                + CausalHarnessSupport.TOXIPROXY.getMappedPort(listenPort) + "/"
                + CausalHarnessSupport.PRIMARY.getDatabaseName();
    }

    private static void removeToxics(Proxy proxy) throws IOException {
        for (Toxic toxic : proxy.toxics().getAll()) {
            toxic.remove();
        }
    }

    private static Proxy createSeoulToReplicaProxy() {
        try {
            ToxiproxyClient client = new ToxiproxyClient(
                    CausalHarnessSupport.TOXIPROXY.getHost(), CausalHarnessSupport.TOXIPROXY.getControlPort());
            return client.createProxy(
                    SEOUL_TO_REPLICA_PROXY_NAME,
                    "0.0.0.0:" + SEOUL_TO_REPLICA_LISTEN_PORT,
                    CausalHarnessSupport.REPLICA.getNetworkAliases().get(0) + ":" + MYSQL_INTERNAL_PORT);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create the Seoul→replica Toxiproxy proxy", e);
        }
    }

    private record Session(User user, Cookie auth, Cookie device) {
    }
}

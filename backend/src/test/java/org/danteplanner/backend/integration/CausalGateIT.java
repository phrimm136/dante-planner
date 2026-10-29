package org.danteplanner.backend.integration;

import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.shared.gtid.GtidCookie;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.UpsertRequests;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The causal GTID cookie gate gives an author read-your-own-write across the stale local replica
 * without any server-side token store.
 *
 * <p>Drives the external contract over the real authenticated planner API (MockMvc through the full
 * security + gate filter chain), never the gate internals, so it survives any implementation choice
 * (filter / interceptor / aspect):</p>
 * <ol>
 *   <li><b>Write sets the cookie.</b> An authenticated write returns a {@code Set-Cookie} carrying a
 *       non-empty Base64url-encoded GTID with {@code HttpOnly}, {@code Secure}, {@code SameSite=Lax}.
 *       The cookie is identified name-agnostically by its GTID-shaped decoded value, so this
 *       asserts the contract, not a hardcoded name.</li>
 *   <li><b>Read-your-own-write routes to primary while the replica lags.</b> Mirroring the
 *       {@link RoutingSeoulIT} probe technique: the author's planner is replicated, replication is
 *       stopped, the author writes a fresh primary-only value; a cookie-bearing read observes the
 *       fresh primary value while an ungated read observes the stale replica value.</li>
 *   <li><b>Once caught up, the read serves the replica and clears the cookie.</b> After
 *       {@code startReplica()} + {@code awaitCaughtUp()}, the cookie-bearing read observes the value
 *       from the replica and the gate clears its cookie ({@code Max-Age=0}).</li>
 * </ol>
 *
 * <p>Replication is always restored in a {@code finally} (no timing windows, only
 * {@code awaitCaughtUp()}).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import({TestConfig.class, CausalGateIT.RecordingCaptureConfig.class})
class CausalGateIT extends CausalHarnessSupport {

    private static final String REPLICATED_TITLE = "causal-old-replicated";
    private static final String PRIMARY_ONLY_TITLE = "causal-new-primary-only";

    /** A GTID interval token: a source UUID followed by a colon and a transaction number. */
    private static final Pattern GTID_VALUE = Pattern.compile(
            "[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}:\\d");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbc;

    @Autowired
    private RecordingGtidWriteCapture gtidWriteCapture;

    @DynamicPropertySource
    static void routingProperties(DynamicPropertyRegistry registry) {
        registry.add("datasource.routing.enabled", () -> "true");
        registry.add("datasource.read-sources[0].name", () -> "seoul-replica");
        registry.add("datasource.read-sources[0].url", REPLICA::getJdbcUrl);
        registry.add("datasource.read-sources[0].username", REPLICA::getUsername);
        registry.add("datasource.read-sources[0].password", REPLICA::getPassword);
        registry.add("datasource.read-sources[1].name", () -> "primary");
    }

    @Test
    void causalGate_WhenAuthorWriteSetsGtidCookie_ReadYourOwnWriteRoutesPrimaryThenClearsCookieWhenCaughtUp()
            throws Exception {
        User author = TestDataFactory.createTestUser(
                userRepository, "causal-gate-" + UUID.randomUUID() + "@example.com");
        Cookie auth = AuthCookies.accessToken(
                TestDataFactory.generateAccessToken(jwtTokenService, author));
        Cookie device = AuthCookies.freshDeviceId();
        UUID plannerId = UUID.randomUUID();

        try {
            MvcResult created = mockMvc.perform(put("/api/planner/md/" + plannerId).with(withCsrf())
                            .cookie(auth, device)
                            .contentType(APPLICATION_JSON)
                            .content(upsertBody(plannerId, REPLICATED_TITLE)))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn();

            assertGtidCookie(created);

            replicationControl.awaitCaughtUp();

            replicationControl.stopReplica();

            MvcResult updated = mockMvc.perform(put("/api/planner/md/" + plannerId + "?force=true")
                            .with(withCsrf())
                            .cookie(auth, device)
                            .contentType(APPLICATION_JSON)
                            .content(upsertBody(plannerId, PRIMARY_ONLY_TITLE)))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn();

            GateCookie freshCookie = assertGtidCookie(updated);

            MvcResult laggingRead = mockMvc.perform(get("/api/planner/md/" + plannerId)
                            .cookie(auth, device, gateCookie(freshCookie)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title", is(PRIMARY_ONLY_TITLE)))
                    .andReturn();

            assertGateCookieNotCleared(laggingRead, freshCookie.name());

            mockMvc.perform(get("/api/planner/md/" + plannerId)
                            .cookie(auth, device))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title", is(REPLICATED_TITLE)));

            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();

            MvcResult caughtUpRead = mockMvc.perform(get("/api/planner/md/" + plannerId)
                            .cookie(auth, device, gateCookie(freshCookie)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title", is(PRIMARY_ONLY_TITLE)))
                    .andReturn();

            assertGateCookieCleared(caughtUpRead, freshCookie.name());
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void causalGate_WhenTokenNamesForeignServerUuid_ReadClearsCookie() throws Exception {
        User author = TestDataFactory.createTestUser(
                userRepository, "causal-foreign-" + UUID.randomUUID() + "@example.com");
        Cookie auth = AuthCookies.accessToken(
                TestDataFactory.generateAccessToken(jwtTokenService, author));
        Cookie device = AuthCookies.freshDeviceId();
        UUID plannerId = UUID.randomUUID();
        mockMvc.perform(put("/api/planner/md/" + plannerId).with(withCsrf())
                        .cookie(auth, device)
                        .contentType(APPLICATION_JSON)
                        .content(upsertBody(plannerId, REPLICATED_TITLE)))
                .andExpect(status().is2xxSuccessful());
        replicationControl.awaitCaughtUp();
        String foreignToken = UUID.randomUUID() + ":1";
        assertThat(gtidSubset(foreignToken, primaryJdbc.queryForObject(
                        "SELECT @@GLOBAL.gtid_executed", String.class)))
                .as("the foreign token %s must name no transaction the primary executed", foreignToken)
                .isFalse();

        MvcResult read = mockMvc.perform(get("/api/planner/md/" + plannerId)
                        .cookie(auth, device, new Cookie(
                                GtidCookie.NAME, GtidCookie.of(foreignToken).getValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is(REPLICATED_TITLE)))
                .andReturn();

        assertGateCookieCleared(read, GtidCookie.NAME);
    }

    @Test
    void rywNoCookieOnRedisOnlyWrite_WhenLogoutCommitsNoTx_MintsNoGtidCookie() throws Exception {
        User user = TestDataFactory.createTestUser(
                userRepository, "logout-gate-" + UUID.randomUUID() + "@example.com");
        Cookie auth = AuthCookies.accessToken(
                TestDataFactory.generateAccessToken(jwtTokenService, user));
        Cookie device = AuthCookies.freshDeviceId();

        MvcResult result = mockMvc.perform(post("/api/auth/logout").with(withCsrf())
                        .cookie(auth, device))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        boolean mintedGtidCookie = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(header -> GTID_VALUE.matcher(decodedCookieValue(header)).find());
        assertThat(mintedGtidCookie)
                .as("logout must mint no GTID cookie among %s",
                        result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .isFalse();
    }

    /**
     * Regression test for the capture reading session state off the wrong connection. The old
     * afterCommit synchronization looked the connection up through the lazy proxy, which
     * materialised a fresh pooled one carrying no session state, so every commit silently took the
     * superset fallback. Coverage assertions cannot catch that — a superset gates correctly — so
     * this is the only test that fails if capture stops reaching the connection that committed.
     */
    @Test
    void ownGtidTracker_WhenWriteCommits_ReportsTransactionOwnGtid() throws Exception {
        User author = TestDataFactory.createTestUser(
                userRepository, "gtid-tracker-author-" + UUID.randomUUID() + "@example.com");
        Cookie auth = AuthCookies.accessToken(
                TestDataFactory.generateAccessToken(jwtTokenService, author));
        Cookie device = AuthCookies.freshDeviceId();
        UUID plannerId = UUID.randomUUID();
        int trackerBefore = gtidWriteCapture.trackerSourced();

        MvcResult written = mockMvc.perform(put("/api/planner/md/" + plannerId).with(withCsrf())
                        .cookie(auth, device)
                        .contentType(APPLICATION_JSON)
                        .content(upsertBody(plannerId, "gtid-tracker-draft")))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        assertThat(gtidWriteCapture.trackerSourced() - trackerBefore)
                .as("the write's GTID must come from the OWN_GTID tracker; zero means capture lost "
                        + "the committing connection and fell back to SELECT @@gtid_executed, which "
                        + "still gates correctly and so passes every other assertion here")
                .isGreaterThanOrEqualTo(1);
        String cookieGtidSet = new String(
                Base64.getUrlDecoder().decode(assertGtidCookie(written).value()),
                StandardCharsets.UTF_8);
        assertThat(cookieGtidSet)
                .as("a tracker-sourced cookie names this request's commits, not the primary's whole "
                        + "executed history")
                .matches(GTID_VALUE.pattern() + ".*");
    }

    private boolean gtidSubset(String candidate, String containing) {
        Long subset = primaryJdbc.queryForObject(
                "SELECT GTID_SUBSET(?, ?)", Long.class, candidate, containing);
        return subset != null && subset == 1L;
    }

    /**
     * Asserts the response set exactly one gate cookie: a {@code Set-Cookie} whose Base64url-decoded
     * value is a non-empty GTID, marked {@code HttpOnly}, {@code Secure}, {@code SameSite=Lax}. The
     * cookie is matched by its GTID-shaped decoded value rather than a hardcoded name; the wire value
     * is encoded because raw GTID sets can contain cookie-illegal commas.
     */
    private static GateCookie assertGtidCookie(MvcResult result) {
        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        String header = setCookies.stream()
                .filter(h -> GTID_VALUE.matcher(decodedCookieValue(h)).find())
                .findFirst()
                .orElse(null);
        assertThat(header)
                .as("a Set-Cookie carrying the transaction GTID (identified by its GTID-shaped "
                        + "decoded value) among %s", setCookies)
                .isNotNull();
        assertThat(decodedCookieValue(header)).as("gate cookie GTID value").isNotBlank();
        assertThat(header).as("gate cookie attributes")
                .contains("HttpOnly")
                .contains("Secure")
                .containsIgnoringCase("SameSite=Lax");
        return new GateCookie(cookieName(header), cookieValue(header));
    }

    /**
     * Asserts the response cleared the gate cookie: a {@code Set-Cookie} for the same name with a
     * {@code Max-Age=0} expiry (or an emptied value).
     */
    private static void assertGateCookieCleared(MvcResult result, String cookieName) {
        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        String header = setCookies.stream()
                .filter(h -> cookieName.equals(cookieName(h)))
                .findFirst()
                .orElse(null);
        assertThat(header)
                .as("a Set-Cookie clearing the gate cookie '%s' among %s", cookieName, setCookies)
                .isNotNull();
        assertThat(header.contains("Max-Age=0") || cookieValue(header).isEmpty())
                .as("gate cookie '%s' cleared (Max-Age=0 or emptied): %s", cookieName, header)
                .isTrue();
    }

    private static void assertGateCookieNotCleared(MvcResult result, String cookieName) {
        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies)
                .as("a lagging but valid token keeps the gate cookie '%s'", cookieName)
                .noneMatch(h -> cookieName.equals(cookieName(h))
                        && (h.contains("Max-Age=0") || cookieValue(h).isEmpty()));
    }

    private static Cookie gateCookie(GateCookie gateCookie) {
        return new Cookie(gateCookie.name(), gateCookie.value());
    }

    private static String cookieName(String setCookieHeader) {
        String pair = setCookieHeader.split(";", 2)[0];
        int eq = pair.indexOf('=');
        return eq < 0 ? pair.trim() : pair.substring(0, eq).trim();
    }

    private static String cookieValue(String setCookieHeader) {
        String pair = setCookieHeader.split(";", 2)[0];
        int eq = pair.indexOf('=');
        return eq < 0 ? "" : pair.substring(eq + 1).trim();
    }

    /** Base64url-decodes a Set-Cookie value; yields an empty string for non-Base64url values. */
    private static String decodedCookieValue(String setCookieHeader) {
        try {
            return new String(
                    Base64.getUrlDecoder().decode(cookieValue(setCookieHeader)),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private String upsertBody(UUID id, String title) throws Exception {
        UpsertPlannerRequest request = UpsertRequests.draft().id(id.toString()).title(title).build();
        return objectMapper.writeValueAsString(request);
    }

    private record GateCookie(String name, String value) {
    }

    /**
     * Replaces the gate's {@link GtidWriteCapture} with a delegating subclass that counts
     * {@code recordCommit} invocations, making "the transaction manager registered a
     * synchronization for each committed transaction" observable from outside the request —
     * the production accumulator state is thread-local and already cleared when
     * {@code mockMvc.perform} returns.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class RecordingCaptureConfig {

        @Bean
        @Primary
        RecordingGtidWriteCapture recordingGtidWriteCapture(MeterRegistry meterRegistry) {
            return new RecordingGtidWriteCapture(meterRegistry);
        }
    }

    static class RecordingGtidWriteCapture extends GtidWriteCapture {

        private final AtomicInteger commitRecordings = new AtomicInteger();
        private final AtomicInteger trackerSourced = new AtomicInteger();

        RecordingGtidWriteCapture(MeterRegistry meterRegistry) {
            super(meterRegistry);
        }

        @Override
        public void recordCommit(String gtid, boolean fromTracker) {
            commitRecordings.incrementAndGet();
            if (fromTracker) {
                trackerSourced.incrementAndGet();
            }
            super.recordCommit(gtid, fromTracker);
        }

        int commitRecordings() {
            return commitRecordings.get();
        }

        /** Commits whose GTID came from the server's OWN_GTID tracker rather than the superset. */
        int trackerSourced() {
            return trackerSourced.get();
        }
    }
}

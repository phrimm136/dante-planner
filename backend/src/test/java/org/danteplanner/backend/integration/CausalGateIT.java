package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
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
 * Phase-8 acceptance test (INV3): the causal GTID cookie gate gives an author read-your-own-write
 * across the stale local replica without any server-side token store.
 *
 * <p>Drives the external contract over the real authenticated planner API (MockMvc through the full
 * security + gate filter chain), never the gate internals, so it survives any implementation choice
 * (filter / interceptor / aspect):</p>
 * <ol>
 *   <li><b>Write sets the cookie.</b> An authenticated write returns a {@code Set-Cookie} carrying a
 *       non-empty Base64url-encoded GTID with {@code HttpOnly}, {@code Secure}, {@code SameSite=Lax}.
 *       The cookie is identified name-agnostically by its GTID-shaped decoded value (the implementer
 *       fixes the name in green), so this asserts the contract, not a hardcoded name.</li>
 *   <li><b>Read-your-own-write routes to primary while the replica lags.</b> Mirroring the
 *       {@link RoutingSeoulIT} probe technique: the author's planner is replicated, replication is
 *       stopped, the author writes a fresh primary-only value; a cookie-bearing read observes the
 *       fresh primary value while an ungated read observes the stale replica value.</li>
 *   <li><b>Once caught up, the read serves the replica and clears the cookie.</b> After
 *       {@code startReplica()} + {@code awaitCaughtUp()}, the cookie-bearing read observes the value
 *       from the replica and the gate clears its cookie ({@code Max-Age=0}).</li>
 * </ol>
 *
 * <p>Replication is always restored in a {@code finally} (INV4: no timing windows, only
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

    /**
     * The transactions a first publish commits: the main one, the {@code AFTER_COMMIT}
     * {@code REQUIRES_NEW} filter rebuild, and the notification fan-out. Each is pinned to a row
     * this test owns, so the count is a property of the fixtures rather than of whatever else the
     * shared harness database happens to hold.
     */
    private static final int PUBLISH_TRANSACTIONS = 3;

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
        registry.add("datasource.replica.enabled", () -> "true");
        registry.add("datasource.replica.url", REPLICA::getJdbcUrl);
        registry.add("datasource.replica.username", REPLICA::getUsername);
        registry.add("datasource.replica.password", REPLICA::getPassword);
    }

    @Test
    @DisplayName("Author write sets a GTID cookie; the cookie-bearing read routes to the fresh primary while the replica lags, then serves the replica and clears the cookie once caught up")
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

            GateCookie writeCookie = assertGtidCookie(created);

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

            mockMvc.perform(get("/api/planner/md/" + plannerId)
                            .cookie(auth, device, gateCookie(freshCookie)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title", is(PRIMARY_ONLY_TITLE)));

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
    @DisplayName("A Redis-only logout commits no MySQL transaction, so no read-your-writes GTID cookie is minted")
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
     * A first publish commits more than once on the
     * request thread — the main transaction, then the AFTER_COMMIT {@code REQUIRES_NEW} listener
     * transactions (filter rebuild, notification fan-out). Two properties are pinned through a
     * real request: the transaction manager registered a commit capture for EACH of those
     * transactions (the recording decorator observed one {@code recordCommit} per committed GTID),
     * and the minted cookie covers every GTID the request committed, so a follow-up replica read
     * gates past the filter rebuild, not only the main commit.
     *
     * <p>The fan-out writes only for users who asked for publication notices, so the test creates
     * its own subscriber: without one the fan-out transaction is empty, commits no GTID, and the
     * request's transaction count becomes a property of whichever neighbour last left a
     * {@code user_settings} row in the shared harness database. Each of the three transactions is
     * asserted through a row this test owns, and the counts are read off the request's own capture
     * and cookie rather than off {@code @@GLOBAL.gtid_executed} — the primary is shared with every
     * other harness class, whose contexts stay cached and keep committing, so a window on the
     * global set counts transactions this request never made.</p>
     *
     * <p>Coverage, not equality: the cookie must gate past every commit, and a superset satisfies
     * that as well as the exact union does — which is what this asserts.</p>
     *
     * <p>Which branch supplied the value is pinned separately by
     * {@code ownGtidTracker_WhenWriteCommits_ReportsTransactionOwnGtid}.</p>
     */
    @Test
    @DisplayName("A first publish commits more than once; every commit is captured and the ryw cookie gates past all of them")
    void ownGtidUnionAcrossCommits_WhenPublishCommitsTwice_EveryCommitIsCapturedAndCookieCoversAll()
            throws Exception {
        User author = TestDataFactory.createTestUser(
                userRepository, "gtid-union-author-" + UUID.randomUUID() + "@example.com");
        User subscriber = TestDataFactory.createTestUser(
                userRepository, "gtid-union-subscriber-" + UUID.randomUUID() + "@example.com");
        subscribeToPublications(subscriber.getId());
        Cookie auth = AuthCookies.accessToken(
                TestDataFactory.generateAccessToken(jwtTokenService, author));
        Cookie device = AuthCookies.freshDeviceId();
        UUID plannerId = UUID.randomUUID();

        mockMvc.perform(put("/api/planner/md/" + plannerId).with(withCsrf())
                        .cookie(auth, device)
                        .contentType(APPLICATION_JSON)
                        .content(upsertBody(plannerId, "gtid-union-draft")))
                .andExpect(status().is2xxSuccessful());

        int recordingsBefore = gtidWriteCapture.commitRecordings();

        MvcResult published = mockMvc.perform(put("/api/planner/md/" + plannerId + "/publish")
                        .with(withCsrf())
                        .cookie(auth, device)
                        .contentType(APPLICATION_JSON)
                        .content("{\"published\":true}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        int recordingsDuringPublish = gtidWriteCapture.commitRecordings() - recordingsBefore;
        String executedAfter = primaryJdbc.queryForObject(
                "SELECT @@GLOBAL.gtid_executed", String.class);
        String cookieGtidSet = new String(
                Base64.getUrlDecoder().decode(assertGtidCookie(published).value()),
                StandardCharsets.UTF_8);

        assertThat(countPublished(plannerId))
                .as("the main transaction must have published this planner")
                .isPositive();
        assertThat(countByPlannerId("planner_entity_filter", plannerId))
                .as("the filter rebuild (the AFTER_COMMIT REQUIRES_NEW commit) must have "
                        + "populated the entity index")
                .isPositive();
        assertThat(countNotifications(subscriber.getId(), plannerId))
                .as("the fan-out (the other AFTER_COMMIT REQUIRES_NEW commit) must have notified "
                        + "this test's own subscriber")
                .isPositive();
        assertThat(recordingsDuringPublish)
                .as("the transaction manager must register a commit capture for EACH of the %s "
                        + "transactions the request committed, not only the main one",
                        PUBLISH_TRANSACTIONS)
                .isGreaterThanOrEqualTo(PUBLISH_TRANSACTIONS);
        assertThat(countTransactions(cookieGtidSet))
                .as("the ryw cookie (%s) must union all %s of the request's commits, so a replica "
                        + "read gates past the filter rebuild and the fan-out, not only the main "
                        + "commit", cookieGtidSet, PUBLISH_TRANSACTIONS)
                .isGreaterThanOrEqualTo(PUBLISH_TRANSACTIONS);
        assertThat(gtidSubset(cookieGtidSet, executedAfter))
                .as("the cookie (%s) must name transactions the primary committed (%s)",
                        cookieGtidSet, executedAfter)
                .isTrue();
    }

    /**
     * Regression test for the capture reading session state off the wrong connection. The old
     * afterCommit synchronization looked the connection up through the lazy proxy, which
     * materialised a fresh pooled one carrying no session state, so every commit silently took the
     * superset fallback. Coverage assertions cannot catch that — a superset gates correctly — so
     * this is the only test that fails if capture stops reaching the connection that committed.
     */
    @Test
    @DisplayName("A committed write reports its own GTID through the session tracker, not the superset fallback")
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

    private int countByPlannerId(String table, UUID plannerId) {
        Integer count = primaryJdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE planner_id = UUID_TO_BIN(?)",
                Integer.class, plannerId.toString());
        return count == null ? 0 : count;
    }

    private int countPublished(UUID plannerId) {
        Integer count = primaryJdbc.queryForObject(
                "SELECT COUNT(*) FROM planner_publication "
                        + "WHERE planner_id = UUID_TO_BIN(?) AND published = TRUE",
                Integer.class, plannerId.toString());
        return count == null ? 0 : count;
    }

    /** Sums the transaction count over a canonical {@code gtid_set} ({@code uuid:a-b[:c-d],...}). */
    private static long countTransactions(String gtidSet) {
        if (gtidSet == null || gtidSet.isBlank()) {
            return 0;
        }
        long total = 0;
        for (String sourceSet : gtidSet.replaceAll("\\s+", "").split(",")) {
            String[] parts = sourceSet.split(":");
            for (int i = 1; i < parts.length; i++) {
                int dash = parts[i].indexOf('-');
                total += dash < 0
                        ? 1
                        : Long.parseLong(parts[i].substring(dash + 1))
                                - Long.parseLong(parts[i].substring(0, dash)) + 1;
            }
        }
        return total;
    }

    /** Opts a user into publication notices, which is what makes the fan-out write a row. */
    private void subscribeToPublications(Long userId) {
        primaryJdbc.update(
                "INSERT INTO user_settings (user_id, sync_enabled, notify_comments, "
                        + "notify_recommendations, notify_new_publications) "
                        + "VALUES (?, FALSE, TRUE, TRUE, TRUE)",
                userId);
    }

    private int countNotifications(Long userId, UUID plannerId) {
        Integer count = primaryJdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications "
                        + "WHERE user_id = ? AND planner_id = UUID_TO_BIN(?)",
                Integer.class, userId, plannerId.toString());
        return count == null ? 0 : count;
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
        UpsertPlannerRequest request = new UpsertPlannerRequest(
                id.toString(), "5F", title, PlannerStatus.DRAFT, TestDataFactory.VALID_CONTENT, 7,
                PlannerType.MIRROR_DUNGEON, null, null);
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

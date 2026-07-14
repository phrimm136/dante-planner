package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
 *       non-empty GTID with {@code HttpOnly}, {@code Secure}, {@code SameSite=Lax}. The cookie is
 *       identified name-agnostically by its GTID-shaped value (the implementer fixes the name in
 *       green), so this asserts the contract, not a hardcoded name.</li>
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
@Import(TestConfig.class)
class CausalGateIT extends CausalHarnessSupport {

    private static final String REPLICATED_TITLE = "causal-old-replicated";
    private static final String PRIMARY_ONLY_TITLE = "causal-new-primary-only";

    /** A GTID interval token: a source UUID followed by a colon and a transaction number. */
    private static final Pattern GTID_VALUE = Pattern.compile(
            "[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}:\\d");

    /** Minimal planner content that passes {@code PlannerContentValidator}. */
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

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
    void causalGate_authorWriteSetsGtidCookie_readYourOwnWriteRoutesPrimaryThenClearsCookieWhenCaughtUp()
            throws Exception {
        User author = TestDataFactory.createTestUser(
                userRepository, "causal-gate-" + UUID.randomUUID() + "@example.com");
        Cookie auth = new Cookie("accessToken",
                TestDataFactory.generateAccessToken(jwtTokenService, author));
        Cookie device = new Cookie("deviceId", UUID.randomUUID().toString());
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

    /**
     * Asserts the response set exactly one gate cookie: a {@code Set-Cookie} whose value is a
     * non-empty GTID, marked {@code HttpOnly}, {@code Secure}, {@code SameSite=Lax}. The cookie is
     * matched by its GTID-shaped value rather than a hardcoded name.
     */
    private static GateCookie assertGtidCookie(MvcResult result) {
        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        String header = setCookies.stream()
                .filter(h -> GTID_VALUE.matcher(cookieValue(h)).find())
                .findFirst()
                .orElse(null);
        assertThat(header)
                .as("a Set-Cookie carrying the transaction GTID (identified by its GTID-shaped value) "
                        + "among %s", setCookies)
                .isNotNull();
        assertThat(cookieValue(header)).as("gate cookie GTID value").isNotBlank();
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

    private String upsertBody(UUID id, String title) throws Exception {
        UpsertPlannerRequest request = new UpsertPlannerRequest(
                id.toString(), "5F", title, PlannerStatus.DRAFT, VALID_CONTENT, 7,
                PlannerType.MIRROR_DUNGEON, null, null);
        return objectMapper.writeValueAsString(request);
    }

    private record GateCookie(String name, String value) {
    }
}

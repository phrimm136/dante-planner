package org.danteplanner.backend.integration;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import({TestConfig.class, RecordingGtidWriteCapture.Config.class})
class CausalReadIT extends ReplicaRoutingHarnessSupport {

    private static final String REPLICATED_TITLE = "causal-old-replicated";
    private static final String PRIMARY_ONLY_TITLE = "causal-new-primary-only";
    private static final String REACHES_THE_FIRST_TRANSACTION = ".*:1-.*";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    @Qualifier("stringRedisTemplate")
    private StringRedisTemplate markerRedis;

    @Autowired
    private RecordingGtidWriteCapture writeCapture;

    @Test
    void authorRead_WhenTheReplicaLagsTheAuthorsWrite_ReturnsTheWrite() throws Exception {
        Session author = session("causal-read-");
        UUID plannerId = UUID.randomUUID();

        try {
            save(author, plannerId, REPLICATED_TITLE);
            replicationControl.awaitCaughtUp();
            replicationControl.stopReplica();
            save(author, plannerId, PRIMARY_ONLY_TITLE);

            assertReads(author, plannerId, PRIMARY_ONLY_TITLE);

            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();

            assertReads(author, plannerId, PRIMARY_ONLY_TITLE);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void redisOnlyWrite_WhenLogoutCommitsNoTransaction_CapturesNothing() throws Exception {
        Session user = session("causal-logout-");
        writeCapture.forget();

        mockMvc.perform(post("/api/auth/logout").with(withCsrf()).cookie(user.auth(), user.device()))
                .andExpect(status().is2xxSuccessful());

        assertThat(writeCapture.taken()).isEmpty();
    }

    /**
     * Coverage assertions cannot catch a capture that reads session state off the wrong connection:
     * every commit then takes the superset fallback, and a superset still marks correctly.
     */
    @Test
    void ownGtidTracker_WhenWriteCommits_RaisesTheMarkerToTheTransactionsOwnGtid() throws Exception {
        Session author = session("causal-tracker-");
        UUID plannerId = UUID.randomUUID();
        writeCapture.forget();

        save(author, plannerId, PRIMARY_ONLY_TITLE);

        GtidWriteCapture.Capture capture = writeCapture.taken().getFirst();
        assertThat(capture.tracked())
                .as("the write's GTID must come from the OWN_GTID tracker; a fallback to SELECT @@gtid_executed "
                        + "still marks correctly and so passes every other assertion here")
                .isTrue();
        assertThat(capture.gtidSet())
                .as("a tracker-sourced capture names this request's commits, not the primary's whole history")
                .doesNotMatch(REACHES_THE_FIRST_TRANSACTION);
        assertThat(markerRedis.<String, String>opsForHash().entries(new FreshnessScope.Planner(plannerId).key()))
                .containsExactlyEntriesOf(Map.of(
                        capture.gtidSet().substring(0, capture.gtidSet().indexOf(':')).toLowerCase(Locale.ROOT),
                        capture.gtidSet().substring(capture.gtidSet().lastIndexOf('-') + 1)));
    }

    private Session session(String emailPrefix) {
        User user = TestDataFactory.createTestUser(userRepository, emailPrefix + UUID.randomUUID() + "@example.com");
        return new Session(AuthCookies.accessToken(TestDataFactory.generateAccessToken(jwtTokenService, user)),
                AuthCookies.freshDeviceId());
    }

    private void save(Session author, UUID plannerId, String title) throws Exception {
        UpsertPlannerRequest request = UpsertRequests.draft().id(plannerId.toString()).title(title).build();
        mockMvc.perform(put("/api/planner/md/" + plannerId + "?force=true").with(withCsrf())
                        .cookie(author.auth(), author.device())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is2xxSuccessful());
    }

    private void assertReads(Session author, UUID plannerId, String title) throws Exception {
        mockMvc.perform(get("/api/planner/md/" + plannerId).cookie(author.auth(), author.device()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is(title)));
    }

    private record Session(Cookie auth, Cookie device) {
    }
}

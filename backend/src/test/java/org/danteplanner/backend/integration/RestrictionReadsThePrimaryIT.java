package org.danteplanner.backend.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.moderation.service.UserModerationService;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class RestrictionReadsThePrimaryIT extends ReplicaRoutingHarnessSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private UserModerationService userModerationService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    @Qualifier("replicaJdbcTemplate")
    private JdbcTemplate replicaJdbcTemplate;

    @Test
    void comment_WhenTheBanHasRespondedButNotReplicated_Returns403() throws Exception {
        User author = TestDataFactory.createTestUser(userRepository, "restriction-primary-author@example.com");
        User admin = TestDataFactory.createAdmin(userRepository, "restriction-primary-admin@example.com");
        User reader = TestDataFactory.createTestUser(userRepository, "restriction-primary-reader@example.com");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, author, true);
        replicationControl.awaitCaughtUp();

        try {
            replicationControl.stopReplica();
            userModerationService.banUser(admin.getId(), reader.getId(), "restriction read path");

            assertThat(replicaJdbcTemplate.queryForObject(
                    "SELECT banned_at IS NULL FROM users WHERE id = ?", Boolean.class, reader.getId()))
                    .as("the replica must still hold the reader unbanned, or the write proves nothing")
                    .isTrue();

            mockMvc.perform(post("/api/planner/{id}/comments", planner.getId()).with(withCsrf())
                            .cookie(AuthCookies.accessToken(TestDataFactory.generateAccessToken(jwtTokenService, reader)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"written after the ban answered\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_BANNED"));
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }
}

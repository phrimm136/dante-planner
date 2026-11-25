package org.danteplanner.backend.moderation;

import javax.sql.DataSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.support.StoredComments;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A moderator restricted after their token was issued still carries the MODERATOR role claim, so
 * the refusal can only come from re-reading the actor's row.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class RestrictedModeratorAuthorityIT extends SharedMySqlContainerSupport {

    private static final String REASON = "{\"reason\":\"Content violates community guidelines\"}";

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerCommentRepository plannerCommentRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    private User owner;
    private User moderator;
    private User admin;
    private Planner planner;
    private Cookie moderatorCookie;
    private Cookie adminCookie;

    @BeforeEach
    void setUp() {
        owner = TestDataFactory.createTestUser(userRepository, "owner@example.com");
        moderator = TestDataFactory.createModerator(userRepository, "mod@example.com");
        admin = TestDataFactory.createAdmin(userRepository, "admin@example.com");
        planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        moderatorCookie = AuthCookies.accessToken(TestDataFactory.generateAccessToken(jwtTokenService, moderator));
        adminCookie = AuthCookies.accessToken(TestDataFactory.generateAccessToken(jwtTokenService, admin));
    }

    private void ban(User actor) {
        transactionTemplate.executeWithoutResult(status ->
                userRepository.ban(actor.getId(), actor.getRole(), admin.getId(), Instant.now()));
    }

    private void timeOut(User actor) {
        transactionTemplate.executeWithoutResult(status ->
                userRepository.timeOut(
                        actor.getId(), actor.getRole(), Instant.now().plus(1, ChronoUnit.HOURS), Instant.now()));
    }

    private Planner reloaded() {
        return plannerRepository.findById(planner.getId()).orElseThrow();
    }

    @Test
    void takedownPlanner_WhenActorBanned_Returns403AndPlannerStaysPublished() throws Exception {
        ban(moderator);

        mockMvc.perform(post("/api/moderation/planner/{id}/takedown", planner.getId()).with(withCsrf())
                        .cookie(moderatorCookie)
                        .contentType(APPLICATION_JSON)
                        .content(REASON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BANNED"));

        assertThat(reloaded().isTakenDown()).isFalse();
        assertThat(reloaded().isPublished()).isTrue();
    }

    @Test
    void takedownPlanner_WhenActorTimedOut_Returns403AndPlannerStaysPublished() throws Exception {
        timeOut(moderator);

        mockMvc.perform(post("/api/moderation/planner/{id}/takedown", planner.getId()).with(withCsrf())
                        .cookie(moderatorCookie)
                        .contentType(APPLICATION_JSON)
                        .content(REASON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_TIMED_OUT"));

        assertThat(reloaded().isTakenDown()).isFalse();
        assertThat(reloaded().isPublished()).isTrue();
    }

    @Test
    void takedownPlanner_WhenAdminActorBanned_Returns403AndPlannerStaysPublished() throws Exception {
        ban(admin);

        mockMvc.perform(post("/api/moderation/planner/{id}/takedown", planner.getId()).with(withCsrf())
                        .cookie(adminCookie)
                        .contentType(APPLICATION_JSON)
                        .content(REASON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BANNED"));

        assertThat(reloaded().isTakenDown()).isFalse();
    }

    @Test
    void takedownPlanner_WhenActorUnrestricted_Returns200AndTakesDown() throws Exception {
        mockMvc.perform(post("/api/moderation/planner/{id}/takedown", planner.getId()).with(withCsrf())
                        .cookie(moderatorCookie)
                        .contentType(APPLICATION_JSON)
                        .content(REASON))
                .andExpect(status().isOk());

        assertThat(reloaded().isTakenDown()).isTrue();
    }

    @Test
    void unpublishPlanner_WhenActorBanned_Returns403AndPlannerStaysPublished() throws Exception {
        ban(moderator);

        mockMvc.perform(put("/api/moderation/planner/{id}/unpublish", planner.getId()).with(withCsrf())
                        .cookie(moderatorCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BANNED"));

        assertThat(reloaded().isPublished()).isTrue();
    }

    @Test
    void hideFromRecommended_WhenActorBanned_Returns403AndPlannerStaysListed() throws Exception {
        ban(moderator);

        mockMvc.perform(post("/api/moderation/planner/{id}/hide-from-recommended", planner.getId()).with(withCsrf())
                        .cookie(moderatorCookie)
                        .contentType(APPLICATION_JSON)
                        .content(REASON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BANNED"));

        assertThat(reloaded().isHiddenFromRecommended()).isFalse();
    }

    @Test
    void unhideFromRecommended_WhenActorBanned_Returns403AndPlannerStaysHidden() throws Exception {
        Planner hidden = reloaded();
        hidden.hideFromRecommended(admin.getId(), "Hidden before the ban", Instant.now());
        plannerRepository.save(hidden);
        ban(moderator);

        mockMvc.perform(post("/api/moderation/planner/{id}/unhide-from-recommended", planner.getId()).with(withCsrf())
                        .cookie(moderatorCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BANNED"));

        assertThat(reloaded().isHiddenFromRecommended()).isTrue();
    }

    @Test
    void deleteComment_WhenActorBanned_Returns403AndCommentSurvives() throws Exception {
        PlannerComment comment = StoredComments.save(plannerCommentRepository, new JdbcTemplate(dataSource), new PlannerComment(
                planner.getId(), owner.getId(), null, 0), "A comment to be moderated");
        ban(moderator);

        mockMvc.perform(post("/api/moderation/comments/{publicId}/delete", comment.getPublicId()).with(withCsrf())
                        .cookie(moderatorCookie)
                        .contentType(APPLICATION_JSON)
                        .content(REASON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BANNED"));

        assertThat(plannerCommentRepository.findRowById(comment.getId()).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    void timeoutUser_WhenActorBanned_Returns403AndTargetStaysActive() throws Exception {
        ban(moderator);

        mockMvc.perform(post("/api/moderation/user/{suffix}/timeout", owner.getUsernameSuffix()).with(withCsrf())
                        .cookie(moderatorCookie)
                        .contentType(APPLICATION_JSON)
                        .content("{\"durationMinutes\":60,\"reason\":\"Repeated harassment reports\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BANNED"));

        assertThat(userRepository.findById(owner.getId()).orElseThrow().getTimeoutUntil()).isNull();
    }

    @Test
    void banUser_WhenAdminActorTimedOut_Returns403AndTargetStaysUnbanned() throws Exception {
        timeOut(admin);

        mockMvc.perform(post("/api/moderation/user/{suffix}/ban", owner.getUsernameSuffix()).with(withCsrf())
                        .cookie(adminCookie)
                        .contentType(APPLICATION_JSON)
                        .content(REASON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_TIMED_OUT"));

        assertThat(userRepository.findById(owner.getId()).orElseThrow().getBannedAt()).isNull();
    }

    @Test
    void takedownPlanner_WhenActorDemoted_Returns403ForbiddenAndPlannerStaysPublished() throws Exception {
        transactionTemplate.executeWithoutResult(status ->
                userRepository.changeRole(
                        moderator.getId(), UserRole.MODERATOR, UserRole.NORMAL, Instant.now()));

        mockMvc.perform(post("/api/moderation/planner/{id}/takedown", planner.getId()).with(withCsrf())
                        .cookie(moderatorCookie)
                        .contentType(APPLICATION_JSON)
                        .content(REASON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(reloaded().isTakenDown()).isFalse();
        assertThat(reloaded().isPublished()).isTrue();
    }

    @Test
    void listUsers_WhenActorBanned_Returns403() throws Exception {
        ban(moderator);

        mockMvc.perform(get("/api/moderation/users").cookie(moderatorCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BANNED"));
    }

    @Test
    void changeRole_WhenAdminActorDemotedToModerator_Returns403ForbiddenAndRoleStays() throws Exception {
        transactionTemplate.executeWithoutResult(status ->
                userRepository.changeRole(
                        admin.getId(), UserRole.ADMIN, UserRole.MODERATOR, Instant.now()));

        mockMvc.perform(put("/api/admin/user/{id}/role", owner.getId()).with(withCsrf())
                        .cookie(adminCookie)
                        .contentType(APPLICATION_JSON)
                        .content("{\"role\":\"MODERATOR\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(userRepository.findById(owner.getId()).orElseThrow().getRole()).isEqualTo(UserRole.NORMAL);
    }

    @Test
    void timeoutUser_WhenActorBannedAndBodyInvalid_Returns403NotValidationError() throws Exception {
        ban(moderator);

        mockMvc.perform(post("/api/moderation/user/{suffix}/timeout", owner.getUsernameSuffix()).with(withCsrf())
                        .cookie(moderatorCookie)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BANNED"));
    }

    @Test
    void takedownPlanner_WhenActorAccountDeleted_Returns403Forbidden() throws Exception {
        transactionTemplate.executeWithoutResult(status ->
                userRepository.softDelete(
                        moderator.getId(), Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS)));

        mockMvc.perform(post("/api/moderation/planner/{id}/takedown", planner.getId()).with(withCsrf())
                        .cookie(moderatorCookie)
                        .contentType(APPLICATION_JSON)
                        .content(REASON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(reloaded().isTakenDown()).isFalse();
    }

    @Test
    void takedownPlanner_WhenTokenNamesNoUserRow_Returns403Forbidden() throws Exception {
        Cookie orphanCookie = AuthCookies.accessToken(
                jwtTokenService.generateAccessToken(Long.MAX_VALUE, UserRole.MODERATOR));

        mockMvc.perform(post("/api/moderation/planner/{id}/takedown", planner.getId()).with(withCsrf())
                        .cookie(orphanCookie)
                        .contentType(APPLICATION_JSON)
                        .content(REASON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(reloaded().isTakenDown()).isFalse();
    }

    @Test
    void takedownPlanner_WhenActorBanned_RendersTheBanRefusalOfAnOrdinaryRoute() throws Exception {
        ban(moderator);

        JsonNode privileged = problemBody(post("/api/moderation/planner/{id}/takedown", planner.getId())
                .with(withCsrf())
                .cookie(moderatorCookie)
                .contentType(APPLICATION_JSON)
                .content(REASON));
        JsonNode ordinary = problemBody(post("/api/planner/md/{id}/publish", UUID.randomUUID())
                .with(withCsrf())
                .cookie(moderatorCookie));

        assertThat(ordinary.path("code").asString()).isEqualTo("USER_BANNED");
        assertThat(fieldNames(privileged)).isEqualTo(fieldNames(ordinary));
        for (String field : List.of("type", "title", "status", "detail", "code", "message")) {
            assertThat(privileged.get(field)).as(field).isEqualTo(ordinary.get(field));
        }
    }

    @Test
    void removeTimeout_WhenActorBanned_Returns403AndTargetStaysTimedOut() throws Exception {
        timeOut(owner);
        ban(moderator);

        mockMvc.perform(post("/api/moderation/user/{suffix}/clear-timeout", owner.getUsernameSuffix()).with(withCsrf())
                        .cookie(moderatorCookie)
                        .contentType(APPLICATION_JSON)
                        .content(REASON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BANNED"));

        assertThat(userRepository.findById(owner.getId()).orElseThrow().getTimeoutUntil()).isAfter(Instant.now());
    }

    @Test
    void unbanUser_WhenActorBanned_Returns403AndTargetStaysBanned() throws Exception {
        ban(owner);
        ban(moderator);

        mockMvc.perform(post("/api/moderation/user/{suffix}/unban", owner.getUsernameSuffix()).with(withCsrf())
                        .cookie(moderatorCookie)
                        .contentType(APPLICATION_JSON)
                        .content(REASON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BANNED"));

        assertThat(userRepository.findById(owner.getId()).orElseThrow().getBannedAt()).isNotNull();
    }

    private JsonNode problemBody(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static Set<String> fieldNames(JsonNode body) {
        Set<String> names = new TreeSet<>();
        names.addAll(body.propertyNames());
        return names;
    }
}

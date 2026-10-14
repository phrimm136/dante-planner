package org.danteplanner.backend.integration;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerSubscription;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerSubscriptionRepository;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.UpsertRequests;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.danteplanner.backend.support.Rejections.assertRejected;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class RestrictionEnforcementIT extends SharedMySqlContainerSupport {

    private static final String BANNED_CODE = "USER_BANNED";
    private static final String TIMED_OUT_CODE = "USER_TIMED_OUT";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerCommentRepository commentRepository;

    @Autowired
    private PlannerSubscriptionRepository subscriptionRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private User author;
    private Planner publishedPlanner;
    private PlannerComment comment;

    @BeforeEach
    void setUp() {
        author = TestDataFactory.createTestUser(userRepository, "restriction-author@example.com");
        publishedPlanner = TestDataFactory.createTestPlanner(plannerRepository, author, true);
        comment = commentRepository.save(new PlannerComment(
                publishedPlanner.getId(), author.getId(), "a comment to engage with", null, 0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void plannerUpvote_WhenTimedOut_Returns403WithTheTimeoutCode() throws Exception {
        User reader = timedOut(reader());

        perform(post("/api/planner/md/{id}/upvote", publishedPlanner.getId())
                .content("{\"voteType\":\"UP\"}"), reader)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(TIMED_OUT_CODE));
    }

    @Test
    void commentUpvote_WhenTimedOut_Returns403WithTheTimeoutCode() throws Exception {
        User reader = timedOut(reader());

        perform(post("/api/comments/{id}/upvote", comment.getPublicId()), reader)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(TIMED_OUT_CODE));
    }

    @Test
    void plannerReport_WhenTimedOut_Returns403WithTheTimeoutCode() throws Exception {
        User reader = timedOut(reader());

        perform(post("/api/planner/md/{id}/report", publishedPlanner.getId()), reader)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(TIMED_OUT_CODE));
    }

    @Test
    void commentReport_WhenTimedOut_Returns403WithTheTimeoutCode() throws Exception {
        User reader = timedOut(reader());

        perform(post("/api/comments/{id}/report", comment.getPublicId())
                .content("{\"reason\":\"spam\"}"), reader)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(TIMED_OUT_CODE));
    }

    @Test
    void subscribe_WhenBanned_Returns403AndLeavesNoSubscription() throws Exception {
        User reader = banned(reader());

        perform(post("/api/planner/md/{id}/subscribe", publishedPlanner.getId()), reader)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(BANNED_CODE));

        assertThat(subscriptionRepository.findByUserIdAndPlannerId(reader.getId(), publishedPlanner.getId()))
                .isEmpty();
    }

    @Test
    void subscribe_WhenTimedOut_Returns403AndLeavesNoSubscription() throws Exception {
        User reader = timedOut(reader());

        perform(post("/api/planner/md/{id}/subscribe", publishedPlanner.getId()), reader)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(TIMED_OUT_CODE));

        assertThat(subscriptionRepository.findByUserIdAndPlannerId(reader.getId(), publishedPlanner.getId()))
                .isEmpty();
    }

    @Test
    void unsubscribe_WhenAlreadySubscribedAndBanned_Returns200AndTurnsTheSubscriptionOff() throws Exception {
        User reader = reader();
        perform(post("/api/planner/md/{id}/subscribe", publishedPlanner.getId()), reader)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(true));
        banned(reader);

        perform(post("/api/planner/md/{id}/subscribe", publishedPlanner.getId()), reader)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(false));

        assertThat(subscriptionRepository.findByUserIdAndPlannerId(reader.getId(), publishedPlanner.getId()))
                .map(PlannerSubscription::isEnabled)
                .contains(false);
    }

    @Test
    void reply_WhenTimedOut_Returns403WithTheTimeoutCode() throws Exception {
        User reader = timedOut(reader());

        perform(post("/api/comments/{id}/replies", comment.getPublicId())
                .content("{\"content\":\"a reply\"}"), reader)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(TIMED_OUT_CODE));
    }

    @Test
    void createPrivatePlanner_WhenBanned_Succeeds() throws Exception {
        User owner = banned(reader());
        UUID plannerId = UUID.randomUUID();

        perform(put("/api/planner/md/{id}", plannerId)
                .content(json(UpsertRequests.draft().id(plannerId.toString()).status(null).build())), owner)
                .andExpect(status().isCreated());
    }

    @Test
    void deleteOwnPlanner_WhenBanned_Succeeds() throws Exception {
        User owner = reader();
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        banned(owner);

        perform(delete("/api/planner/md/{id}", planner.getId()), owner)
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteOwnComment_WhenBanned_Succeeds() throws Exception {
        User reader = reader();
        PlannerComment own = commentRepository.save(new PlannerComment(
                publishedPlanner.getId(), reader.getId(), "my own words", null, 0));
        banned(reader);

        perform(delete("/api/comments/{id}", own.getPublicId()), reader)
                .andExpect(status().isNoContent());
    }

    @Test
    void unpublishOwnPlanner_WhenBanned_Succeeds() throws Exception {
        User owner = reader();
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        banned(owner);

        perform(post("/api/planner/md/{id}/unpublish", planner.getId()), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false));
    }

    @Test
    void updateSettings_WhenBanned_Succeeds() throws Exception {
        User reader = banned(reader());

        perform(put("/api/user/settings").content("{\"notifyComments\":true}"), reader)
                .andExpect(status().isOk());
    }

    @Test
    void markAllNotificationsRead_WhenBanned_Succeeds() throws Exception {
        User reader = banned(reader());

        perform(post("/api/notifications/mark-all-read"), reader)
                .andExpect(status().isOk());
    }

    @Test
    void deleteOwnAccount_WhenBanned_Succeeds() throws Exception {
        User reader = banned(reader());

        perform(delete("/api/user/me"), reader)
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void changeEpithet_WhenBanned_Succeeds() throws Exception {
        User reader = banned(reader());

        perform(put("/api/user/me/username-epithet").content("{\"epithet\":\"NAIVE\"}"), reader)
                .andExpect(status().isOk());
    }

    @Test
    void upsertPublishedPlanner_WhenBannedWithAStaleSyncVersion_Returns403NotTheConflict() throws Exception {
        User owner = reader();
        Planner planner = TestDataFactory.planner(owner).published(true).syncVersion(3L).save(plannerRepository);
        String stale = json(UpsertRequests.resending(planner).title("Renamed").syncVersion(1L).build());

        perform(put("/api/planner/md/{id}", planner.getId()).content(stale), owner)
                .andExpect(status().isConflict());
        banned(owner);

        perform(put("/api/planner/md/{id}", planner.getId()).content(stale), owner)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(BANNED_CODE));
    }

    @Test
    void upsertPrivatePlanner_WhenBanned_Saves() throws Exception {
        User owner = reader();
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, false);
        banned(owner);

        perform(put("/api/planner/md/{id}", planner.getId())
                .content(json(UpsertRequests.resending(planner).title("Renamed").build())), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"));
    }

    @Test
    void restrictedByStateRequest_WhenItCommitsWithoutADecision_ThrowsAndRollsBack() {
        User reader = reader();

        Throwable undecided = withinRequest("toggleSubscription", reader, () -> new TransactionTemplate(
                transactionManager).executeWithoutResult(status -> subscriptionRepository.insert(
                        new PlannerSubscription(reader.getId(), publishedPlanner.getId()))));

        assertThat(undecided)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PlannerEngagementController.toggleSubscription is @RestrictedByState");
        assertThat(subscriptionRepository.findByUserIdAndPlannerId(reader.getId(), publishedPlanner.getId()))
                .isEmpty();
    }

    @Test
    void restrictedRequest_WhenItRunsTwoWriteTransactions_ChecksOnlyTheFirst() {
        User reader = reader();
        Planner second = TestDataFactory.createTestPlanner(plannerRepository, author, true);
        TransactionTemplate writes = new TransactionTemplate(transactionManager);

        Throwable failure = withinRequest("createComment", reader, () -> {
            writes.executeWithoutResult(status -> subscriptionRepository.insert(
                    new PlannerSubscription(reader.getId(), publishedPlanner.getId())));
            banned(reader);
            writes.executeWithoutResult(status -> subscriptionRepository.insert(
                    new PlannerSubscription(reader.getId(), second.getId())));
        });

        assertThat(failure).isNull();
        assertThat(subscriptionRepository.findByUserIdAndPlannerId(reader.getId(), second.getId())).isPresent();
    }

    @Test
    void restrictedRequest_WhenTheFirstWriteTransactionIsRefused_RollsItBackAndUnbindsIt() {
        User reader = banned(reader());

        Throwable refused = withinRequest("createComment", reader, () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> subscriptionRepository.insert(
                        new PlannerSubscription(reader.getId(), publishedPlanner.getId()))));

        assertRejected(() -> {
            if (refused != null) {
                throw refused;
            }
        }).withCode(BANNED_CODE);
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
        assertThat(subscriptionRepository.findByUserIdAndPlannerId(reader.getId(), publishedPlanner.getId()))
                .isEmpty();
    }

    private User reader() {
        return TestDataFactory.createTestUser(userRepository, "restriction-reader@example.com");
    }

    private User timedOut(User user) {
        user.setTimeoutUntil(Instant.now().plus(Duration.ofHours(1)));
        return userRepository.save(user);
    }

    private User banned(User user) {
        user.setBannedAt(Instant.now());
        return userRepository.save(user);
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Throwable withinRequest(String handlerName, User principal, Runnable body) {
        HandlerMethod handler = handlerMapping.getHandlerMethods().values().stream()
                .filter(candidate -> candidate.getMethod().getName().equals(handlerName))
                .findFirst()
                .orElseThrow();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, handler);
        RequestAttributes previousRequest = RequestContextHolder.getRequestAttributes();
        SecurityContext previousSecurity = SecurityContextHolder.getContext();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal.getId(), null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            body.run();
            return null;
        } catch (RuntimeException failure) {
            return failure;
        } finally {
            RequestContextHolder.setRequestAttributes(previousRequest);
            SecurityContextHolder.setContext(previousSecurity);
        }
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, User principal) throws Exception {
        return mockMvc.perform(request.with(withCsrf())
                .cookie(cookieOf(principal))
                .contentType(MediaType.APPLICATION_JSON));
    }

    private Cookie cookieOf(User principal) {
        return AuthCookies.accessToken(TestDataFactory.generateAccessToken(jwtTokenService, principal));
    }
}

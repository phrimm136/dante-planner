package org.danteplanner.backend.integration;

import javax.sql.DataSource;

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
import org.springframework.jdbc.core.JdbcTemplate;
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

import tools.jackson.databind.ObjectMapper;

import org.danteplanner.backend.support.StoredComments;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
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
    private DataSource dataSource;

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
        comment = StoredComments.save(commentRepository, new JdbcTemplate(dataSource), new PlannerComment(
                publishedPlanner.getId(), author.getId(), null, 0), "a comment to engage with");
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

        assertThat(subscriptionRepository.findEnabledOf(reader.getId(), publishedPlanner.getId()))
                .isEmpty();
    }

    @Test
    void subscribe_WhenTimedOut_Returns403AndLeavesNoSubscription() throws Exception {
        User reader = timedOut(reader());

        perform(post("/api/planner/md/{id}/subscribe", publishedPlanner.getId()), reader)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(TIMED_OUT_CODE));

        assertThat(subscriptionRepository.findEnabledOf(reader.getId(), publishedPlanner.getId()))
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

        assertThat(subscriptionRepository.findEnabledOf(reader.getId(), publishedPlanner.getId()))
                .contains(false);
    }

    @Test
    void setSubscription_WhenRepeatedAndRemovedTwice_AnswersTheRequestedStateEachTime() throws Exception {
        User reader = reader();

        for (int repeat = 0; repeat < 2; repeat++) {
            perform(put("/api/planner/md/{id}/subscription", publishedPlanner.getId()), reader)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plannerId").value(publishedPlanner.getId().toString()))
                    .andExpect(jsonPath("$.subscribed").value(true));
            assertThat(subscriptionRepository.findEnabledOf(reader.getId(), publishedPlanner.getId())).contains(true);
        }
        for (int repeat = 0; repeat < 2; repeat++) {
            perform(delete("/api/planner/md/{id}/subscription", publishedPlanner.getId()), reader)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plannerId").value(publishedPlanner.getId().toString()))
                    .andExpect(jsonPath("$.subscribed").value(false));
            assertThat(subscriptionRepository.findEnabledOf(reader.getId(), publishedPlanner.getId())).contains(false);
        }
    }

    @Test
    void removeSubscription_WhenNoneExists_AnswersUnsubscribedAndWritesNoRow() throws Exception {
        User reader = reader();

        perform(delete("/api/planner/md/{id}/subscription", publishedPlanner.getId()), reader)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(false));

        assertThat(subscriptionRepository.findEnabledOf(reader.getId(), publishedPlanner.getId())).isEmpty();
    }

    @Test
    void setSubscription_WhenBannedOrTimedOut_Returns403AndWritesNoRow() throws Exception {
        User banned = banned(reader());
        User timedOut = timedOut(reader());

        perform(put("/api/planner/md/{id}/subscription", publishedPlanner.getId()), banned)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(BANNED_CODE));
        perform(put("/api/planner/md/{id}/subscription", publishedPlanner.getId()), timedOut)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(TIMED_OUT_CODE));

        assertThat(subscriptionRepository.findEnabledOf(banned.getId(), publishedPlanner.getId())).isEmpty();
        assertThat(subscriptionRepository.findEnabledOf(timedOut.getId(), publishedPlanner.getId())).isEmpty();
    }

    @Test
    void removeSubscription_WhenSubscribedAndBanned_Returns200AndTurnsTheSubscriptionOff() throws Exception {
        User reader = reader();
        perform(put("/api/planner/md/{id}/subscription", publishedPlanner.getId()), reader)
                .andExpect(status().isOk());
        banned(reader);

        perform(delete("/api/planner/md/{id}/subscription", publishedPlanner.getId()), reader)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(false));

        assertThat(subscriptionRepository.findEnabledOf(reader.getId(), publishedPlanner.getId())).contains(false);
    }

    @Test
    void subscriptionRequests_WhenThePlannerIsMissingOrUnpublished_AnswerAsTheToggleDoes() throws Exception {
        User reader = reader();
        UUID unpublished = TestDataFactory.createTestPlanner(plannerRepository, author, false).getId();

        for (UUID id : List.of(UUID.randomUUID(), unpublished)) {
            for (MockHttpServletRequestBuilder request : subscriptionRequests(id)) {
                perform(request, reader)
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
            }
            assertThat(subscriptionRepository.findEnabledOf(reader.getId(), id)).isEmpty();
        }
    }

    @Test
    void subscriptionRequests_WhenSentByAGuest_AnswerAsTheToggleDoes() throws Exception {
        for (MockHttpServletRequestBuilder request : subscriptionRequests(publishedPlanner.getId())) {
            mockMvc.perform(request.with(withCsrf())).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void subscriptionRequests_WhenSentWithoutACsrfToken_AreRefusedAsTheToggleIs() throws Exception {
        User reader = reader();
        for (MockHttpServletRequestBuilder request : subscriptionRequests(publishedPlanner.getId())) {
            mockMvc.perform(request.cookie(AuthCookies.accessToken(
                            TestDataFactory.generateAccessToken(jwtTokenService, reader))))
                    .andExpect(status().isForbidden());
        }
        assertThat(subscriptionRepository.findEnabledOf(reader.getId(), publishedPlanner.getId())).isEmpty();
    }

    private static List<MockHttpServletRequestBuilder> subscriptionRequests(UUID id) {
        return List.of(post("/api/planner/md/{id}/subscribe", id), put("/api/planner/md/{id}/subscription", id),
                delete("/api/planner/md/{id}/subscription", id));
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
        PlannerComment own = StoredComments.save(commentRepository, new JdbcTemplate(dataSource), new PlannerComment(
                publishedPlanner.getId(), reader.getId(), null, 0), "my own words");
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
                transactionManager).executeWithoutResult(status -> subscriptionRepository.insertIfAbsent(
                        reader.getId(), publishedPlanner.getId(), Instant.now())));

        assertThat(undecided)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PlannerEngagementController.toggleSubscription is @RestrictedByState");
        assertThat(subscriptionRepository.findEnabledOf(reader.getId(), publishedPlanner.getId()))
                .isEmpty();
    }

    @Test
    void restrictedRequest_WhenItRunsTwoWriteTransactions_ChecksOnlyTheFirst() {
        User reader = reader();
        Planner second = TestDataFactory.createTestPlanner(plannerRepository, author, true);
        TransactionTemplate writes = new TransactionTemplate(transactionManager);

        Throwable failure = withinRequest("createComment", reader, () -> {
            writes.executeWithoutResult(status -> subscriptionRepository.insertIfAbsent(
                        reader.getId(), publishedPlanner.getId(), Instant.now()));
            banned(reader);
            writes.executeWithoutResult(status -> subscriptionRepository.insertIfAbsent(
                        reader.getId(), second.getId(), Instant.now()));
        });

        assertThat(failure).isNull();
        assertThat(subscriptionRepository.findEnabledOf(reader.getId(), second.getId())).isPresent();
    }

    @Test
    void restrictedRequest_WhenTheFirstWriteTransactionIsRefused_RollsItBackAndUnbindsIt() {
        User reader = banned(reader());

        Throwable refused = withinRequest("createComment", reader, () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> subscriptionRepository.insertIfAbsent(
                        reader.getId(), publishedPlanner.getId(), Instant.now())));

        assertRejected(() -> {
            if (refused != null) {
                throw refused;
            }
        }).withCode(BANNED_CODE);
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
        assertThat(subscriptionRepository.findEnabledOf(reader.getId(), publishedPlanner.getId()))
                .isEmpty();
    }

    private User reader() {
        return TestDataFactory.createTestUser(userRepository, "restriction-reader@example.com");
    }

    private User timedOut(User user) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                userRepository.timeOut(
                        user.getId(), user.getRole(), Instant.now().plus(Duration.ofHours(1)), Instant.now()));
        return user;
    }

    private User banned(User user) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                userRepository.ban(user.getId(), user.getRole(), null, Instant.now()));
        return user;
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

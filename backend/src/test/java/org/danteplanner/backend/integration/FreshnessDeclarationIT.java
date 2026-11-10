package org.danteplanner.backend.integration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import jakarta.servlet.http.Cookie;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.notification.api.NotificationType;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.shared.freshness.DispatchStamp;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.gtid.GtidCookie;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;
import org.danteplanner.backend.shared.outbox.service.DomainEventDispatcher;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.UpsertRequests;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class FreshnessDeclarationIT extends ReplicaRoutingHarnessSupport {

    private static final String PLANNERS = "/api/planner/md/";
    private static final String REASON_BODY = "{\"reason\":\"freshness declaration\"}";
    private static final String COMMENT_BODY = "{\"content\":\"freshness declaration\"}";
    private static final String UPVOTE_BODY = "{\"voteType\":\"UP\"}";
    private static final String NOTIFICATIONS_OFF_BODY = "{\"enabled\":false}";
    private static final String EPITHET_BODY = "{\"epithet\":\"NAIVE\"}";
    private static final String EDITED_COMMENT_BODY = "{\"content\":\"freshness declaration, edited\"}";
    private static final String SETTINGS_BODY = "{\"syncEnabled\":true}";
    private static final String TIMEOUT_BODY = "{\"durationMinutes\":10,\"reason\":\"freshness declaration\"}";
    private static final String ROLE_BODY = "{\"role\":\"MODERATOR\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserAccountLifecycleService lifecycleService;

    @Autowired
    private UserService userService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PlannerCommandService plannerCommandService;

    @Autowired
    private GtidWriteCapture writeCapture;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    @Qualifier("stringRedisTemplate")
    private StringRedisTemplate markerRedis;

    @Autowired
    private DomainEventDispatcher dispatcher;

    @Autowired
    private DispatchStamp dispatchStamp;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerCommentRepository commentRepository;

    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;

    @Autowired
    @Qualifier("replicaJdbcTemplate")
    private JdbcTemplate replicaJdbcTemplate;

    private record Session(User user, Cookie[] cookies) {

        long id() {
            return user.getId();
        }

        String suffix() {
            return user.getUsernameSuffix();
        }
    }

    private record Published(UUID plannerId, Session owner, Session reader, Session moderator, UUID commentId) {
    }

    private interface RequestFor {

        MockHttpServletRequestBuilder build(Published published) throws Exception;
    }

    private record Row(String name, RequestFor request, Function<Published, Set<FreshnessScope>> scopes) {

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Arguments> writesOnAPublishedPlanner() {
        Function<Published, Set<FreshnessScope>> plannerAndGlobal =
                published -> Set.of(planner(published), new FreshnessScope.Global());
        return Stream.of(
                new Row("owner edit", published -> put(PLANNERS + published.plannerId() + "?force=true")
                        .contentType(APPLICATION_JSON)
                        .content(json(published.plannerId(), "edited-" + UUID.randomUUID()))
                        .cookie(published.owner().cookies()),
                        published -> Set.of(planner(published), new FreshnessScope.User(published.owner().id()),
                                new FreshnessScope.Global())),
                new Row("owner delete", published -> delete(PLANNERS + published.plannerId())
                        .cookie(published.owner().cookies()),
                        published -> Set.of(planner(published), new FreshnessScope.User(published.owner().id()),
                                new FreshnessScope.Global())),
                new Row("owner unpublish", published -> post(PLANNERS + published.plannerId() + "/unpublish")
                        .cookie(published.owner().cookies()), plannerAndGlobal),
                new Row("owner notification toggle", published -> patch(PLANNERS + published.plannerId()
                        + "/notifications")
                        .contentType(APPLICATION_JSON)
                        .content(NOTIFICATIONS_OFF_BODY)
                        .cookie(published.owner().cookies()),
                        published -> Set.of(planner(published))),
                new Row("reader upvote", published -> post(PLANNERS + published.plannerId() + "/upvote")
                        .contentType(APPLICATION_JSON)
                        .content(UPVOTE_BODY)
                        .cookie(published.reader().cookies()), plannerAndGlobal),
                new Row("reader comment", published -> post("/api/planner/" + published.plannerId() + "/comments")
                        .contentType(APPLICATION_JSON)
                        .content(COMMENT_BODY)
                        .cookie(published.reader().cookies()), plannerAndGlobal),
                new Row("reader reply", published -> post("/api/comments/" + published.commentId() + "/replies")
                        .contentType(APPLICATION_JSON)
                        .content(COMMENT_BODY)
                        .cookie(published.reader().cookies()), plannerAndGlobal),
                new Row("author comment delete", published -> delete("/api/comments/" + published.commentId())
                        .cookie(published.reader().cookies()), plannerAndGlobal),
                new Row("moderator comment delete", published -> post("/api/moderation/comments/"
                        + published.commentId() + "/delete")
                        .contentType(APPLICATION_JSON)
                        .content(REASON_BODY)
                        .cookie(published.moderator().cookies()), plannerAndGlobal),
                new Row("moderator takedown", published -> post("/api/moderation/planner/" + published.plannerId()
                        + "/takedown")
                        .contentType(APPLICATION_JSON)
                        .content(REASON_BODY)
                        .cookie(published.moderator().cookies()), plannerAndGlobal),
                new Row("moderator unpublish", published -> put("/api/moderation/planner/" + published.plannerId()
                        + "/unpublish")
                        .cookie(published.moderator().cookies()), plannerAndGlobal),
                new Row("moderator hide from recommended", published -> post("/api/moderation/planner/"
                        + published.plannerId() + "/hide-from-recommended")
                        .contentType(APPLICATION_JSON)
                        .content(REASON_BODY)
                        .cookie(published.moderator().cookies()),
                        published -> Set.of(new FreshnessScope.Global())),
                new Row("author comment edit", published -> put("/api/comments/" + published.commentId())
                        .contentType(APPLICATION_JSON)
                        .content(EDITED_COMMENT_BODY)
                        .cookie(published.reader().cookies()),
                        published -> Set.of(planner(published))),
                new Row("owner comment vote", published -> post("/api/comments/" + published.commentId() + "/upvote")
                        .cookie(published.owner().cookies()),
                        published -> Set.of(planner(published))),
                new Row("author comment notification toggle", published -> patch("/api/comments/"
                        + published.commentId() + "/notifications")
                        .contentType(APPLICATION_JSON)
                        .content(NOTIFICATIONS_OFF_BODY)
                        .cookie(published.reader().cookies()),
                        published -> Set.of(planner(published))),
                new Row("reader subscribe", published -> post(PLANNERS + published.plannerId() + "/subscribe")
                        .cookie(published.reader().cookies()),
                        published -> Set.of(planner(published))),
                new Row("reader planner report", published -> post(PLANNERS + published.plannerId() + "/report")
                        .cookie(published.reader().cookies()),
                        published -> Set.of(planner(published))))
                .map(Arguments::of);
    }

    static Stream<Arguments> moderationsRepeatedOnAnUnchangedTarget() {
        return Stream.of(
                new Row("moderator takedown", published -> post("/api/moderation/planner/" + published.plannerId()
                        + "/takedown")
                        .contentType(APPLICATION_JSON)
                        .content(REASON_BODY)
                        .cookie(published.moderator().cookies()),
                        published -> Set.of(new FreshnessScope.Global())),
                new Row("moderator unpublish", published -> put("/api/moderation/planner/" + published.plannerId()
                        + "/unpublish")
                        .cookie(published.moderator().cookies()),
                        published -> Set.of(new FreshnessScope.Global())),
                new Row("moderator hide from recommended", published -> post("/api/moderation/planner/"
                        + published.plannerId() + "/hide-from-recommended")
                        .contentType(APPLICATION_JSON)
                        .content(REASON_BODY)
                        .cookie(published.moderator().cookies()),
                        published -> Set.of(new FreshnessScope.Global())),
                new Row("moderator comment delete", published -> post("/api/moderation/comments/"
                        + published.commentId() + "/delete")
                        .contentType(APPLICATION_JSON)
                        .content(REASON_BODY)
                        .cookie(published.moderator().cookies()),
                        published -> Set.of(new FreshnessScope.Global())))
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("moderationsRepeatedOnAnUnchangedTarget")
    void moderation_WhenRepeatedOnAnUnchangedTarget_StampsOnlyGlobalForItsAuditRow(Row row) throws Exception {
        Published published = publishedPlannerWithAComment();
        perform(row.request().build(published));

        assertStampsExactly(row.request().build(published), row.scopes().apply(published), candidates(published));
    }

    private record People(Session subject, Session moderator, Session admin, UUID notificationId) {
    }

    private interface RequestOn {

        MockHttpServletRequestBuilder build(People people) throws Exception;
    }

    private record UserRow(String name, RequestOn request, Function<People, Set<FreshnessScope>> scopes) {

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Arguments> writesOnAUser() {
        Function<People, Set<FreshnessScope>> subjectOnly =
                people -> Set.of(new FreshnessScope.User(people.subject().id()));
        Function<People, Set<FreshnessScope>> subjectAndGlobal =
                people -> Set.of(new FreshnessScope.User(people.subject().id()), new FreshnessScope.Global());
        return Stream.of(
                new UserRow("settings save", people -> put("/api/user/settings")
                        .contentType(APPLICATION_JSON)
                        .content(SETTINGS_BODY)
                        .cookie(people.subject().cookies()), subjectOnly),
                new UserRow("inbox mark-read", people -> post("/api/notifications/" + people.notificationId()
                        + "/mark-read")
                        .cookie(people.subject().cookies()), subjectOnly),
                new UserRow("inbox mark-all-read", people -> post("/api/notifications/mark-all-read")
                        .cookie(people.subject().cookies()), subjectOnly),
                new UserRow("inbox delete", people -> delete("/api/notifications/" + people.notificationId())
                        .cookie(people.subject().cookies()), subjectOnly),
                new UserRow("inbox delete-all", people -> delete("/api/notifications/all")
                        .cookie(people.subject().cookies()), subjectOnly),
                new UserRow("admin ban", people -> post("/api/moderation/user/" + people.subject().suffix() + "/ban")
                        .contentType(APPLICATION_JSON)
                        .content(REASON_BODY)
                        .cookie(people.admin().cookies()), subjectAndGlobal),
                new UserRow("admin unban", people -> post("/api/moderation/user/" + people.subject().suffix()
                        + "/unban")
                        .contentType(APPLICATION_JSON)
                        .content(REASON_BODY)
                        .cookie(people.admin().cookies()), subjectAndGlobal),
                new UserRow("moderator timeout", people -> post("/api/moderation/user/" + people.subject().suffix()
                        + "/timeout")
                        .contentType(APPLICATION_JSON)
                        .content(TIMEOUT_BODY)
                        .cookie(people.moderator().cookies()), subjectAndGlobal),
                new UserRow("moderator clear-timeout", people -> post("/api/moderation/user/"
                        + people.subject().suffix() + "/clear-timeout")
                        .contentType(APPLICATION_JSON)
                        .content(REASON_BODY)
                        .cookie(people.moderator().cookies()), subjectAndGlobal),
                new UserRow("admin role change", people -> put("/api/admin/user/" + people.subject().id() + "/role")
                        .contentType(APPLICATION_JSON)
                        .content(ROLE_BODY)
                        .cookie(people.admin().cookies()), subjectAndGlobal))
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("writesOnAUser")
    void writeOnAUser_WhenItCommitsAndAnswers_StampsExactlyItsScopesWithItsGtid(UserRow row) throws Exception {
        People people = peopleWithANotification();

        assertStampsExactly(row.request().build(people), row.scopes().apply(people), candidates(people));
    }

    @Test
    void commentReport_WhenItCommitsAndAnswers_StampsNothing() throws Exception {
        Published published = publishedPlannerWithAComment();
        Set<FreshnessScope> candidates = candidates(published);

        MvcResult result = perform(post("/api/comments/" + published.commentId() + "/report")
                .contentType(APPLICATION_JSON)
                .content(REASON_BODY)
                .cookie(published.owner().cookies()));

        long requestSequence = primarySequence(requestGtid(result));
        assertThat(keysAt(markers(candidates), requestSequence))
                .as("markers raised to the report's own sequence %s", requestSequence)
                .isEmpty();
    }

    @Test
    void createAccount_WhenItCommitsInsideARequestWindow_DeclaresItsUserAndGlobal() {
        writeCapture.begin();
        FreshnessDeclarations.begin();
        try {
            User created = userService.findOrCreateUser("google", Map.of(
                    "id", "freshness-create-" + UUID.randomUUID(),
                    "email", "freshness-create-" + UUID.randomUUID() + "@example.com"));

            assertThat(FreshnessDeclarations.declared()).containsExactlyInAnyOrder(
                    new FreshnessScope.Global(), new FreshnessScope.User(created.getId()));
            assertThat(writeCapture.takeCapturedGtid()).isPresent();
        } finally {
            writeCapture.clear();
            FreshnessDeclarations.clear();
        }
    }

    @ParameterizedTest
    @MethodSource("writesOnAPublishedPlanner")
    void writeOnAPublishedPlanner_WhenItCommitsAndAnswers_StampsExactlyItsScopesWithItsGtid(Row row)
            throws Exception {
        Published published = publishedPlannerWithAComment();

        assertStampsExactly(row.request().build(published), row.scopes().apply(published), candidates(published));
    }

    @Test
    void unhideFromRecommended_WhenItCommitsAndAnswers_StampsOnlyGlobal() throws Exception {
        Published published = publishedPlannerWithAComment();
        perform(post("/api/moderation/planner/" + published.plannerId() + "/hide-from-recommended")
                .contentType(APPLICATION_JSON)
                .content(REASON_BODY)
                .cookie(published.moderator().cookies()));

        assertStampsExactly(post("/api/moderation/planner/" + published.plannerId() + "/unhide-from-recommended")
                        .cookie(published.moderator().cookies()),
                Set.of(new FreshnessScope.Global()), candidates(published));
    }

    @Test
    void create_WhenItCommitsAndAnswers_StampsItsPlannerAndItsOwner() throws Exception {
        Session owner = session("freshness-create");
        UUID plannerId = UUID.randomUUID();

        assertStampsExactly(put(PLANNERS + plannerId)
                        .contentType(APPLICATION_JSON)
                        .content(json(plannerId, "created"))
                        .cookie(owner.cookies()),
                Set.of(new FreshnessScope.Planner(plannerId), new FreshnessScope.User(owner.id())),
                candidates(owner, plannerId));
    }

    @Test
    void draftEdit_WhenItCommitsAndAnswers_StampsItsPlannerAndItsOwner() throws Exception {
        Session owner = session("freshness-draft-edit");
        UUID plannerId = draft(owner);

        assertStampsExactly(put(PLANNERS + plannerId + "?force=true")
                        .contentType(APPLICATION_JSON)
                        .content(json(plannerId, "edited-draft"))
                        .cookie(owner.cookies()),
                Set.of(new FreshnessScope.Planner(plannerId), new FreshnessScope.User(owner.id())),
                candidates(owner, plannerId));
    }

    @Test
    void draftDelete_WhenItCommitsAndAnswers_StampsItsPlannerItsOwnerAndGlobal() throws Exception {
        Session owner = session("freshness-draft-delete");
        UUID plannerId = draft(owner);

        assertStampsExactly(delete(PLANNERS + plannerId).cookie(owner.cookies()),
                Set.of(new FreshnessScope.Planner(plannerId), new FreshnessScope.User(owner.id()),
                        new FreshnessScope.Global()),
                candidates(owner, plannerId));
    }

    @Test
    void publish_WhenItCarriesNoContent_StampsItsPlannerAndGlobal() throws Exception {
        Session owner = session("freshness-publish");
        UUID plannerId = draft(owner);

        assertStampsExactly(post(PLANNERS + plannerId + "/publish").cookie(owner.cookies()),
                Set.of(new FreshnessScope.Planner(plannerId), new FreshnessScope.Global()),
                candidates(owner, plannerId));
    }

    @Test
    void publish_WhenItCarriesChangedContent_AlsoStampsItsOwner() throws Exception {
        Session owner = session("freshness-publish-content");
        UUID plannerId = draft(owner);

        assertStampsExactly(post(PLANNERS + plannerId + "/publish")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpsertRequests.draft()
                                .id(plannerId.toString())
                                .title("published-with-content")
                                .syncVersion(1L)
                                .build()))
                        .cookie(owner.cookies()),
                Set.of(new FreshnessScope.Planner(plannerId), new FreshnessScope.User(owner.id()),
                        new FreshnessScope.Global()),
                candidates(owner, plannerId));
    }

    @Test
    void updateUsernameEpithet_WhenItCommitsAndAnswers_StampsItsUserAndGlobal() throws Exception {
        Session owner = session("freshness-epithet");
        UUID first = published(owner);
        UUID second = published(owner);
        UUID draft = draft(owner);

        assertStampsExactly(put("/api/user/me/username-epithet")
                        .contentType(APPLICATION_JSON)
                        .content(EPITHET_BODY)
                        .cookie(owner.cookies()),
                Set.of(new FreshnessScope.User(owner.id()), new FreshnessScope.Global()),
                candidates(owner, first, second, draft));
    }

    @Test
    void deleteAccount_WhenItCommitsAndAnswers_StampsGlobalAndItsUser() throws Exception {
        Session owner = session("freshness-account-delete");
        UUID first = published(owner);
        UUID second = published(owner);
        UUID draft = draft(owner);

        assertStampsExactly(delete("/api/user/me").cookie(owner.cookies()),
                Set.of(new FreshnessScope.Global(), new FreshnessScope.User(owner.id())),
                candidates(owner, first, second, draft));
    }

    @Test
    void reactivateAccount_WhenItCommitsInsideARequestWindow_DeclaresGlobalAndItsUser()
            throws Exception {
        Session owner = session("freshness-reactivate");
        published(owner);
        published(owner);
        draft(owner);
        perform(delete("/api/user/me").cookie(owner.cookies()));
        writeCapture.begin();
        FreshnessDeclarations.begin();
        try {
            lifecycleService.reactivateAccount(owner.id());

            assertThat(FreshnessDeclarations.declared()).containsExactlyInAnyOrder(
                    new FreshnessScope.Global(), new FreshnessScope.User(owner.id()));
            assertThat(writeCapture.takeCapturedGtid()).isPresent();
        } finally {
            writeCapture.clear();
            FreshnessDeclarations.clear();
        }
    }

    @Test
    void write_WhenItFailsValidation_StampsNothing() throws Exception {
        Session owner = session("freshness-invalid");
        UUID plannerId = draft(owner);
        Set<FreshnessScope> candidates = candidates(owner, plannerId);
        Map<String, Map<String, String>> before = markers(candidates);

        mockMvc.perform(put(PLANNERS + plannerId + "?force=true").with(withCsrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"no planner type or content\"}")
                        .cookie(owner.cookies()))
                .andExpect(status().is4xxClientError());

        assertThat(markers(candidates)).isEqualTo(before);
    }

    @Test
    void read_WhenItAnswers_StampsNothing() throws Exception {
        Session owner = session("freshness-read");
        UUID plannerId = draft(owner);
        replicationControl.awaitCaughtUp();
        Set<FreshnessScope> candidates = candidates(owner, plannerId);
        Map<String, Map<String, String>> before = markers(candidates);

        mockMvc.perform(get(PLANNERS + plannerId).cookie(owner.cookies()))
                .andExpect(status().isOk());

        assertThat(markers(candidates)).isEqualTo(before);
    }

    @Test
    void deletePlanner_WhenItsTransactionRollsBackAfterDeclaring_CapturesNoGtidToStamp() throws Exception {
        Session owner = session("freshness-rollback");
        UUID plannerId = draft(owner);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        writeCapture.begin();
        FreshnessDeclarations.begin();
        try {
            transaction.executeWithoutResult(status -> {
                plannerCommandService.deletePlanner(owner.id(), plannerId);
                status.setRollbackOnly();
            });

            assertThat(FreshnessDeclarations.declared()).contains(new FreshnessScope.Planner(plannerId));
            assertThat(writeCapture.takeCapturedGtid()).isEmpty();

            transaction.executeWithoutResult(status -> plannerCommandService.deletePlanner(owner.id(), plannerId));

            assertThat(writeCapture.takeCapturedGtid()).isPresent();
        } finally {
            writeCapture.clear();
            FreshnessDeclarations.clear();
        }
    }

    private void assertStampsExactly(MockHttpServletRequestBuilder request, Set<FreshnessScope> scopes,
            Set<FreshnessScope> candidates) throws Exception {
        assertThat(candidates).containsAll(scopes);
        Map<String, Map<String, String>> before = markers(candidates);

        MvcResult result = perform(request);

        long requestSequence = primarySequence(requestGtid(result));
        Map<String, Map<String, String>> after = markers(candidates);
        Set<String> expected = scopes.stream().map(FreshnessScope::key).collect(Collectors.toSet());
        expected.forEach(key -> {
            assertThat(after.get(key))
                    .as("marker %s, changed by a request committing sequence %s", key, requestSequence)
                    .isNotEqualTo(before.get(key));
            assertThat(sequenceOf(after.get(key)))
                    .as("marker %s after a request committing sequence %s", key, requestSequence)
                    .isGreaterThanOrEqualTo(requestSequence);
        });
        Set<String> unexpectedAtTheRequest = new HashSet<>(keysAt(after, requestSequence));
        unexpectedAtTheRequest.removeAll(expected);
        assertThat(unexpectedAtTheRequest)
                .as("markers outside %s raised to the request's own sequence %s", expected, requestSequence)
                .isEmpty();
    }

    @Test
    void dispatch_WhenACommentOnAnotherUsersPlannerIsDispatchedInAWindow_RaisesTheOwnerAndThePlannerToTheDispatchCommit() {
        Session owner = session("freshness-dispatch-owner");
        Session commenter = session("freshness-dispatch-commenter");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner.user(), true);
        PlannerComment comment = insertComment(planner.getId(), commenter.id());
        long eventId = recordEvent("COMMENT_RECEIVED", planner.getId(), "commentId", comment.getId());
        FreshnessScope ownerScope = new FreshnessScope.User(owner.id());
        FreshnessScope plannerScope = new FreshnessScope.Planner(planner.getId());
        Set<FreshnessScope> candidates = Set.of(plannerScope, ownerScope,
                new FreshnessScope.User(commenter.id()), new FreshnessScope.Global());
        replicationControl.awaitCaughtUp();
        try {
            replicationControl.stopReplica();
            Map<String, Map<String, String>> after = dispatchInAWindow(eventId, candidates,
                    Set.of(ownerScope.key(), plannerScope.key()));

            long raised = sequenceOf(after.get(ownerScope.key()));
            assertThat(sequenceOf(after.get(plannerScope.key()))).isEqualTo(raised);
            String raisedGtid = primaryUuid() + ":" + raised;
            replicaJdbcTemplate.execute("START REPLICA IO_THREAD");
            applyReplicaUntil("SQL_BEFORE_GTIDS", raisedGtid);
            assertThat(holdsCommentNotification(replicaJdbcTemplate, owner.id(), comment.getId()))
                    .as("the replica stopped just before %s", raisedGtid)
                    .isFalse();
            applyReplicaUntil("SQL_AFTER_GTIDS", raisedGtid);
            assertThat(holdsCommentNotification(replicaJdbcTemplate, owner.id(), comment.getId()))
                    .as("the replica stopped just after %s", raisedGtid)
                    .isTrue();
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    @Test
    void dispatch_WhenTheOwnerCommentsOnTheirOwnPlanner_RaisesOnlyThePlanner() {
        Session owner = session("freshness-self-comment-owner");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner.user(), true);
        PlannerComment comment = insertComment(planner.getId(), owner.id());
        long eventId = recordEvent("COMMENT_RECEIVED", planner.getId(), "commentId", comment.getId());
        FreshnessScope plannerScope = new FreshnessScope.Planner(planner.getId());

        dispatchInAWindow(eventId, Set.of(plannerScope, new FreshnessScope.User(owner.id()),
                new FreshnessScope.Global()), Set.of(plannerScope.key()));

        assertThat(holdsCommentNotification(primaryJdbcTemplate, owner.id(), comment.getId())).isFalse();
    }

    @Test
    void dispatch_WhenAPublishFanoutIsDispatchedInAWindow_RaisesOnlyGlobal() {
        Session author = session("freshness-dispatch-author");
        Session follower = session("freshness-dispatch-follower");
        primaryJdbcTemplate.update(
                "INSERT INTO user_settings (user_id, notify_new_publications) VALUES (?, true)", follower.id());
        try {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, author.user(), true);
            long eventId = recordEvent("PLANNER_PUBLISHED", planner.getId(), "authorId", author.id());

            dispatchInAWindow(eventId, Set.of(new FreshnessScope.Planner(planner.getId()),
                    new FreshnessScope.User(author.id()), new FreshnessScope.User(follower.id()),
                    new FreshnessScope.Global()), Set.of(new FreshnessScope.Global().key()));

            assertThat(publishedFanoutRows(planner.getId(), follower.id()))
                    .as("the opted-in follower's fan-out rows")
                    .isEqualTo(1);
        } finally {
            primaryJdbcTemplate.update(
                    "UPDATE user_settings SET notify_new_publications = false WHERE user_id = ?", follower.id());
        }
    }

    @Test
    void dispatch_WhenAPublishHasNoOptedInRecipient_StillRaisesOnlyGlobal() {
        Session author = session("freshness-dispatch-lone-author");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, author.user(), true);
        long eventId = recordEvent("PLANNER_PUBLISHED", planner.getId(), "authorId", author.id());

        dispatchInAWindow(eventId, Set.of(new FreshnessScope.Planner(planner.getId()),
                new FreshnessScope.User(author.id()), new FreshnessScope.Global()),
                Set.of(new FreshnessScope.Global().key()));

        assertThat(publishedFanoutRows(planner.getId()))
                .as("fan-out rows for this planner: the scenario needs none")
                .isZero();
    }

    @Test
    void dispatch_WhenTheEventWasAlreadyDispatched_RaisesNoMarker() {
        Session owner = session("freshness-redispatch-owner");
        Session commenter = session("freshness-redispatch-commenter");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner.user(), true);
        PlannerComment comment = insertComment(planner.getId(), commenter.id());
        long eventId = recordEvent("COMMENT_RECEIVED", planner.getId(), "commentId", comment.getId());
        Set<FreshnessScope> candidates = Set.of(new FreshnessScope.Planner(planner.getId()),
                new FreshnessScope.User(owner.id()), new FreshnessScope.User(commenter.id()),
                new FreshnessScope.Global());
        dispatchStamp.around(() -> dispatcher.dispatchDomainEvent(eventId));

        dispatchInAWindow(eventId, candidates, Set.of());
    }

    @Test
    void dispatch_WhenTheNotificationInsertWritesNoRow_RaisesOnlyThePlannerForTheThreadPush() {
        Session owner = session("freshness-duplicate-owner");
        Session commenter = session("freshness-duplicate-commenter");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner.user(), true);
        PlannerComment comment = insertComment(planner.getId(), commenter.id());
        notificationRepository.save(new Notification(
                owner.id(), comment.getId().toString(), NotificationType.COMMENT_RECEIVED));
        long eventId = recordEvent("COMMENT_RECEIVED", planner.getId(), "commentId", comment.getId());
        FreshnessScope plannerScope = new FreshnessScope.Planner(planner.getId());

        dispatchInAWindow(eventId, Set.of(plannerScope, new FreshnessScope.User(owner.id()),
                new FreshnessScope.User(commenter.id()), new FreshnessScope.Global()), Set.of(plannerScope.key()));
    }

    private Map<String, Map<String, String>> dispatchInAWindow(long eventId, Set<FreshnessScope> candidates,
            Set<String> expected) {
        Map<String, Map<String, String>> before = markers(candidates);
        long executedBefore = primaryExecutedSequence();

        dispatchStamp.around(() -> dispatcher.dispatchDomainEvent(eventId));

        long executedAfter = primaryExecutedSequence();
        Map<String, Map<String, String>> after = markers(candidates);
        Set<String> raisedByTheDispatch = after.keySet().stream()
                .filter(key -> !Objects.equals(before.get(key), after.get(key)))
                .filter(key -> sequenceOf(after.get(key)) > executedBefore
                        && sequenceOf(after.get(key)) <= executedAfter)
                .collect(Collectors.toSet());
        assertThat(raisedByTheDispatch)
                .as("markers raised to a sequence in (%s, %s], the dispatch's commits", executedBefore, executedAfter)
                .containsExactlyInAnyOrderElementsOf(expected);
        return after;
    }

    private PlannerComment insertComment(UUID plannerId, Long authorId) {
        return new TransactionTemplate(transactionManager).execute(status -> commentRepository.insert(
                new PlannerComment(plannerId, authorId, "freshness dispatch", null, 0)));
    }

    private long recordEvent(String eventType, UUID plannerId, String idKey, Long id) {
        primaryJdbcTemplate.update("""
                INSERT INTO domain_events (event_type, aggregate_id, payload, created_at)
                VALUES (?, UUID_TO_BIN(?), JSON_OBJECT(?, ?), NOW(6))
                """, eventType, plannerId.toString(), idKey, id);
        return primaryJdbcTemplate.queryForObject(
                "SELECT id FROM domain_events WHERE aggregate_id = UUID_TO_BIN(?) AND event_type = ?",
                Long.class, plannerId.toString(), eventType);
    }

    private int publishedFanoutRows(UUID plannerId) {
        Integer rows = primaryJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE content_id = ? AND notification_type = 'PLANNER_PUBLISHED'",
                Integer.class, plannerId.toString());
        return rows == null ? 0 : rows;
    }

    private int publishedFanoutRows(UUID plannerId, Long recipientId) {
        Integer rows = primaryJdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notifications
                WHERE content_id = ? AND notification_type = 'PLANNER_PUBLISHED' AND user_id = ?
                """, Integer.class, plannerId.toString(), recipientId);
        return rows == null ? 0 : rows;
    }

    private String primaryUuid() {
        return primaryJdbcTemplate.queryForObject("SELECT @@server_uuid", String.class).toLowerCase(Locale.ROOT);
    }

    private long primarySequence(String gtidSet) {
        return Long.parseLong(highestSequences(gtidSet).getOrDefault(primaryUuid(), "0"));
    }

    private long primaryExecutedSequence() {
        return primarySequence(primaryJdbcTemplate.queryForObject("SELECT @@GLOBAL.gtid_executed", String.class));
    }

    private long sequenceOf(Map<String, String> marker) {
        return Long.parseLong(marker.getOrDefault(primaryUuid(), "0"));
    }

    private Set<String> keysAt(Map<String, Map<String, String>> markers, long sequence) {
        return markers.entrySet().stream()
                .filter(entry -> sequenceOf(entry.getValue()) == sequence)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private void applyReplicaUntil(String condition, String gtid) {
        replicaJdbcTemplate.execute("START REPLICA SQL_THREAD UNTIL " + condition + " = '" + gtid + "'");
        Hang.until(() -> "No".equals(replicaJdbcTemplate.queryForList("SHOW REPLICA STATUS").get(0)
                .get("Replica_SQL_Running")), "the replica's applier stopping at " + condition + " " + gtid);
    }

    private static boolean holdsCommentNotification(JdbcTemplate source, Long recipientId, Long commentId) {
        Integer rows = source.queryForObject("""
                SELECT COUNT(*) FROM notifications
                WHERE user_id = ? AND content_id = ? AND notification_type = 'COMMENT_RECEIVED'
                """, Integer.class, recipientId, commentId.toString());
        return rows != null && rows > 0;
    }

    private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request.with(withCsrf())).andReturn();
        assertThat(result.getResponse().getStatus())
                .as("%s %s answered %s", result.getRequest().getMethod(), result.getRequest().getRequestURI(),
                        result.getResponse().getContentAsString())
                .isBetween(200, 399);
        return result;
    }

    private Map<String, Map<String, String>> markers(Set<FreshnessScope> candidates) {
        Map<String, Map<String, String>> markers = new HashMap<>();
        candidates.forEach(scope -> markers.put(scope.key(),
                markerRedis.<String, String>opsForHash().entries(scope.key())));
        return markers;
    }

    private static Set<FreshnessScope> candidates(Published published) {
        return Set.of(planner(published), new FreshnessScope.User(published.owner().id()),
                new FreshnessScope.User(published.reader().id()),
                new FreshnessScope.User(published.moderator().id()), new FreshnessScope.Global());
    }

    private static Set<FreshnessScope> candidates(People people) {
        Set<FreshnessScope> candidates = new HashSet<>();
        Stream.of(people.subject(), people.moderator(), people.admin())
                .forEach(session -> candidates.add(new FreshnessScope.User(session.id())));
        candidates.add(new FreshnessScope.Global());
        return candidates;
    }

    private static Set<FreshnessScope> candidates(Session owner, UUID... plannerIds) {
        Set<FreshnessScope> candidates = new HashSet<>();
        candidates.add(new FreshnessScope.User(owner.id()));
        candidates.add(new FreshnessScope.Global());
        for (UUID plannerId : plannerIds) {
            candidates.add(new FreshnessScope.Planner(plannerId));
        }
        return candidates;
    }

    private static String requestGtid(MvcResult result) {
        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        return setCookies.stream()
                .map(header -> header.split(";", 2)[0])
                .filter(pair -> pair.startsWith(GtidCookie.NAME + "="))
                .map(pair -> GtidCookie.decode(pair.substring(GtidCookie.NAME.length() + 1)))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no GTID cookie among " + setCookies));
    }

    private static Map<String, String> highestSequences(String gtidSet) {
        Map<String, Long> highest = new TreeMap<>();
        for (String sourceSet : gtidSet.split(",")) {
            String[] parts = sourceSet.trim().split(":");
            for (int i = 1; i < parts.length; i++) {
                String interval = parts[i];
                long end = Long.parseLong(interval.substring(interval.indexOf('-') + 1));
                highest.merge(parts[0].toLowerCase(Locale.ROOT), end, Math::max);
            }
        }
        Map<String, String> asText = new TreeMap<>();
        highest.forEach((uuid, sequence) -> asText.put(uuid, String.valueOf(sequence)));
        return asText;
    }

    private static FreshnessScope planner(Published published) {
        return new FreshnessScope.Planner(published.plannerId());
    }

    private static String json(UUID plannerId, String title) throws Exception {
        return new ObjectMapper().writeValueAsString(
                UpsertRequests.draft().id(plannerId.toString()).title(title).build());
    }

    private Session session(String prefix) {
        return sessionOf(TestDataFactory.createTestUser(userRepository, prefix + "-" + UUID.randomUUID()
                + "@example.com"));
    }

    private Session moderatorSession() {
        return sessionOf(TestDataFactory.createModerator(userRepository, "freshness-moderator-" + UUID.randomUUID()
                + "@example.com"));
    }

    private Session sessionOf(User user) {
        return new Session(user, new Cookie[] {
            AuthCookies.accessToken(TestDataFactory.generateAccessToken(jwtTokenService, user)),
            AuthCookies.freshDeviceId()});
    }

    private UUID draft(Session owner) throws Exception {
        UUID plannerId = UUID.randomUUID();
        perform(put(PLANNERS + plannerId)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        UpsertRequests.draft().id(plannerId.toString()).title("draft").build()))
                .cookie(owner.cookies()));
        return plannerId;
    }

    private UUID published(Session owner) throws Exception {
        UUID plannerId = draft(owner);
        perform(post(PLANNERS + plannerId + "/publish").cookie(owner.cookies()));
        return plannerId;
    }

    private Published publishedPlannerWithAComment() throws Exception {
        Session owner = session("freshness-owner");
        Session reader = session("freshness-reader");
        UUID plannerId = published(owner);
        MvcResult comment = perform(post("/api/planner/" + plannerId + "/comments")
                .contentType(APPLICATION_JSON)
                .content(COMMENT_BODY)
                .cookie(reader.cookies()));
        UUID commentId = UUID.fromString(objectMapper.readTree(comment.getResponse().getContentAsString())
                .get("id").asString());
        return new Published(plannerId, owner, reader, moderatorSession(), commentId);
    }

    private People peopleWithANotification() {
        Session subject = session("freshness-subject");
        Notification notification = notificationRepository.save(new Notification(
                subject.id(), "freshness-" + UUID.randomUUID(), NotificationType.COMMENT_RECEIVED));
        Session admin = sessionOf(TestDataFactory.createAdmin(userRepository, "freshness-admin-" + UUID.randomUUID()
                + "@example.com"));
        return new People(subject, moderatorSession(), admin, notification.getPublicId());
    }
}

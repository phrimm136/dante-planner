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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.servlet.http.Cookie;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.gtid.GtidCookie;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.UpsertRequests;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;

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
    private PlannerCommandService plannerCommandService;

    @Autowired
    private GtidWriteCapture writeCapture;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    @Qualifier("stringRedisTemplate")
    private StringRedisTemplate markerRedis;

    private record Session(User user, Cookie[] cookies) {

        long id() {
            return user.getId();
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
        Function<Published, Set<FreshnessScope>> entityAndCatalog =
                published -> Set.of(entity(published), new FreshnessScope.PublicCatalog());
        return Stream.of(
                new Row("owner edit", published -> put(PLANNERS + published.plannerId() + "?force=true")
                        .contentType(APPLICATION_JSON)
                        .content(json(published.plannerId(), "edited-" + UUID.randomUUID()))
                        .cookie(published.owner().cookies()),
                        published -> Set.of(entity(published), new FreshnessScope.OwnerList(published.owner().id()),
                                new FreshnessScope.PublicCatalog())),
                new Row("owner delete", published -> delete(PLANNERS + published.plannerId())
                        .cookie(published.owner().cookies()),
                        published -> Set.of(entity(published), new FreshnessScope.OwnerList(published.owner().id()),
                                new FreshnessScope.PublicCatalog())),
                new Row("owner unpublish", published -> post(PLANNERS + published.plannerId() + "/unpublish")
                        .cookie(published.owner().cookies()), entityAndCatalog),
                new Row("owner notification toggle", published -> patch(PLANNERS + published.plannerId()
                        + "/notifications")
                        .contentType(APPLICATION_JSON)
                        .content(NOTIFICATIONS_OFF_BODY)
                        .cookie(published.owner().cookies()),
                        published -> Set.of(entity(published))),
                new Row("reader upvote", published -> post(PLANNERS + published.plannerId() + "/upvote")
                        .contentType(APPLICATION_JSON)
                        .content(UPVOTE_BODY)
                        .cookie(published.reader().cookies()), entityAndCatalog),
                new Row("reader comment", published -> post("/api/planner/" + published.plannerId() + "/comments")
                        .contentType(APPLICATION_JSON)
                        .content(COMMENT_BODY)
                        .cookie(published.reader().cookies()), entityAndCatalog),
                new Row("reader reply", published -> post("/api/comments/" + published.commentId() + "/replies")
                        .contentType(APPLICATION_JSON)
                        .content(COMMENT_BODY)
                        .cookie(published.reader().cookies()), entityAndCatalog),
                new Row("author comment delete", published -> delete("/api/comments/" + published.commentId())
                        .cookie(published.reader().cookies()), entityAndCatalog),
                new Row("moderator comment delete", published -> post("/api/moderation/comments/"
                        + published.commentId() + "/delete")
                        .contentType(APPLICATION_JSON)
                        .content(REASON_BODY)
                        .cookie(published.moderator().cookies()), entityAndCatalog),
                new Row("moderator takedown", published -> post("/api/moderation/planner/" + published.plannerId()
                        + "/takedown")
                        .contentType(APPLICATION_JSON)
                        .content(REASON_BODY)
                        .cookie(published.moderator().cookies()), entityAndCatalog),
                new Row("moderator unpublish", published -> put("/api/moderation/planner/" + published.plannerId()
                        + "/unpublish")
                        .cookie(published.moderator().cookies()), entityAndCatalog),
                new Row("moderator hide from recommended", published -> post("/api/moderation/planner/"
                        + published.plannerId() + "/hide-from-recommended")
                        .contentType(APPLICATION_JSON)
                        .content(REASON_BODY)
                        .cookie(published.moderator().cookies()),
                        published -> Set.of(new FreshnessScope.PublicCatalog())))
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("writesOnAPublishedPlanner")
    void writeOnAPublishedPlanner_WhenItCommitsAndAnswers_StampsExactlyItsScopesWithItsGtid(Row row)
            throws Exception {
        Published published = publishedPlannerWithAComment();

        assertStampsExactly(row.request().build(published), row.scopes().apply(published), candidates(published));
    }

    @Test
    void unhideFromRecommended_WhenItCommitsAndAnswers_StampsOnlyThePublicCatalog() throws Exception {
        Published published = publishedPlannerWithAComment();
        perform(post("/api/moderation/planner/" + published.plannerId() + "/hide-from-recommended")
                .contentType(APPLICATION_JSON)
                .content(REASON_BODY)
                .cookie(published.moderator().cookies()));

        assertStampsExactly(post("/api/moderation/planner/" + published.plannerId() + "/unhide-from-recommended")
                        .cookie(published.moderator().cookies()),
                Set.of(new FreshnessScope.PublicCatalog()), candidates(published));
    }

    @Test
    void create_WhenItCommitsAndAnswers_StampsItsEntityAndItsOwnersList() throws Exception {
        Session owner = session("freshness-create");
        UUID plannerId = UUID.randomUUID();

        assertStampsExactly(put(PLANNERS + plannerId)
                        .contentType(APPLICATION_JSON)
                        .content(json(plannerId, "created"))
                        .cookie(owner.cookies()),
                Set.of(new FreshnessScope.Entity(plannerId), new FreshnessScope.OwnerList(owner.id())),
                candidates(owner, plannerId));
    }

    @Test
    void draftEdit_WhenItCommitsAndAnswers_StampsItsEntityAndItsOwnersList() throws Exception {
        Session owner = session("freshness-draft-edit");
        UUID plannerId = draft(owner);

        assertStampsExactly(put(PLANNERS + plannerId + "?force=true")
                        .contentType(APPLICATION_JSON)
                        .content(json(plannerId, "edited-draft"))
                        .cookie(owner.cookies()),
                Set.of(new FreshnessScope.Entity(plannerId), new FreshnessScope.OwnerList(owner.id())),
                candidates(owner, plannerId));
    }

    @Test
    void draftDelete_WhenItCommitsAndAnswers_StampsItsEntityItsOwnersListAndThePublicCatalog() throws Exception {
        Session owner = session("freshness-draft-delete");
        UUID plannerId = draft(owner);

        assertStampsExactly(delete(PLANNERS + plannerId).cookie(owner.cookies()),
                Set.of(new FreshnessScope.Entity(plannerId), new FreshnessScope.OwnerList(owner.id()),
                        new FreshnessScope.PublicCatalog()),
                candidates(owner, plannerId));
    }

    @Test
    void publish_WhenItCarriesNoContent_StampsItsEntityAndThePublicCatalog() throws Exception {
        Session owner = session("freshness-publish");
        UUID plannerId = draft(owner);

        assertStampsExactly(post(PLANNERS + plannerId + "/publish").cookie(owner.cookies()),
                Set.of(new FreshnessScope.Entity(plannerId), new FreshnessScope.PublicCatalog()),
                candidates(owner, plannerId));
    }

    @Test
    void publish_WhenItCarriesChangedContent_AlsoStampsItsOwnersList() throws Exception {
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
                Set.of(new FreshnessScope.Entity(plannerId), new FreshnessScope.OwnerList(owner.id()),
                        new FreshnessScope.PublicCatalog()),
                candidates(owner, plannerId));
    }

    @Test
    void updateUsernameEpithet_WhenItCommitsAndAnswers_StampsOnlyItsAuthor() throws Exception {
        Session owner = session("freshness-epithet");
        UUID first = published(owner);
        UUID second = published(owner);
        UUID draft = draft(owner);

        assertStampsExactly(put("/api/user/me/username-epithet")
                        .contentType(APPLICATION_JSON)
                        .content(EPITHET_BODY)
                        .cookie(owner.cookies()),
                Set.of(new FreshnessScope.Author(owner.id())),
                candidates(owner, first, second, draft));
    }

    @Test
    void deleteAccount_WhenItCommitsAndAnswers_StampsThePublicCatalogAndItsAuthor() throws Exception {
        Session owner = session("freshness-account-delete");
        UUID first = published(owner);
        UUID second = published(owner);
        UUID draft = draft(owner);

        assertStampsExactly(delete("/api/user/me").cookie(owner.cookies()),
                Set.of(new FreshnessScope.PublicCatalog(), new FreshnessScope.Author(owner.id())),
                candidates(owner, first, second, draft));
    }

    @Test
    void reactivateAccount_WhenItCommitsInsideARequestWindow_DeclaresThePublicCatalogAndItsAuthor()
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
                    new FreshnessScope.PublicCatalog(), new FreshnessScope.Author(owner.id()));
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

            assertThat(FreshnessDeclarations.declared()).contains(new FreshnessScope.Entity(plannerId));
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

        Map<String, String> requestGtid = highestSequences(requestGtid(result));
        Map<String, Map<String, String>> after = markers(candidates);
        Set<String> changed = after.keySet().stream()
                .filter(key -> !Objects.equals(before.get(key), after.get(key)))
                .collect(Collectors.toSet());
        assertThat(changed)
                .as("the markers of this fixture's planners, users and the catalog that this request changed")
                .containsExactlyInAnyOrderElementsOf(scopes.stream().map(FreshnessScope::key).toList());
        scopes.forEach(scope -> assertThat(after.get(scope.key()))
                .as("marker %s after a request committing %s", scope.key(), requestGtid)
                .containsAllEntriesOf(requestGtid));
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
        return Set.of(entity(published), new FreshnessScope.OwnerList(published.owner().id()),
                new FreshnessScope.OwnerList(published.reader().id()),
                new FreshnessScope.OwnerList(published.moderator().id()), new FreshnessScope.PublicCatalog());
    }

    private static Set<FreshnessScope> candidates(Session owner, UUID... plannerIds) {
        Set<FreshnessScope> candidates = new HashSet<>();
        candidates.add(new FreshnessScope.OwnerList(owner.id()));
        candidates.add(new FreshnessScope.Author(owner.id()));
        candidates.add(new FreshnessScope.PublicCatalog());
        for (UUID plannerId : plannerIds) {
            candidates.add(new FreshnessScope.Entity(plannerId));
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

    private static FreshnessScope entity(Published published) {
        return new FreshnessScope.Entity(published.plannerId());
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
                .get("id").asText());
        return new Published(plannerId, owner, reader, moderatorSession(), commentId);
    }
}

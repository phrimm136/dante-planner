package org.danteplanner.backend.integration;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import jakarta.servlet.http.Cookie;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.comment.dto.CreateCommentRequest;
import org.danteplanner.backend.comment.dto.CreateCommentResponse;
import org.danteplanner.backend.comment.service.CommentCommandService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.shared.sse.SseService;
import org.danteplanner.backend.shared.sse.SuspensionType;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.SseStream;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.dto.UpdateUserSettingsRequest;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserSettingsService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-node SSE fan-out over Redis pub/sub.
 *
 * <p>Models a Seoul-style pod that publishes to the Oregon Redis <b>primary</b> yet subscribes to
 * its <b>local</b> Redis: an event published on the primary via {@link SsePublisher} must reach the
 * subscriber listening on the local endpoint and be dispatched to this node's {@link SseService}
 * carrying the payload — true fan-out with no sticky sessions, and no notify-then-refetch (the
 * recipient patches its cache from the payload).</p>
 *
 * <p>The harness reuses {@link CausalHarnessSupport}'s full app context (MySQL primary/replica +
 * {@code AUTH_REDIS}). The publisher targets the primary (bound to {@code redis.auth.*}); the
 * subscriber binds to the distinct {@code redis.sse-local.*} endpoint. Both point at the same
 * {@code AUTH_REDIS} container: the code contract (publish via the primary factory, subscribe via a
 * distinct sse-local factory, fan out to local emitters) is proven; replication propagation is a
 * Redis deployment guarantee, out of scope.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class SseFanoutIT extends CausalHarnessSupport {

    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(5);
    private static final int SETTINGS_REPUBLISH_ATTEMPTS = 20;
    private static final Duration SETTINGS_DELIVERY_WAIT = Duration.ofMillis(500);

    @DynamicPropertySource
    static void sseLocalRedis(DynamicPropertyRegistry registry) {
        registry.add("redis.sse-local.host", AUTH_REDIS::getRedisHost);
        registry.add("redis.sse-local.port", AUTH_REDIS::getRedisPort);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private SsePublisher ssePublisher;

    @Autowired
    private CommentCommandService commentCommandService;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserSettingsService userSettingsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fanout_WhenPublishedOnPrimary_DeliveredToLocalEmitterWithPayload() {
        User user = user("sse-fanout");
        String entityId = "planner-" + UUID.randomUUID();
        Map<String, Object> payload = Map.of("plannerId", entityId, "title", "Refactored deck");

        try (SseStream stream = SseStream.user(port, session(user))) {
            ssePublisher.publishUserEvent(user.getId(), null, SseEventType.COMMENT_ADDED, entityId, payload);

            assertThat(stream.next(SseEventType.COMMENT_ADDED.getValue(), DELIVERY_TIMEOUT)).contains(entityId);
        }
    }

    @Test
    void sseBroadcastCrossPod_WhenPublishedOnPrimary_DeliveredWithRawPayload() {
        User recipient = user("sse-broadcast-recipient");
        enableNewPublicationNotices(recipient);
        User author = user("sse-broadcast-author");
        String plannerId = "planner-broadcast-" + UUID.randomUUID();
        Map<String, Object> payload = Map.of("plannerId", plannerId, "plannerTitle", "Cross-pod build");

        try (SseStream stream = SseStream.user(port, session(recipient))) {
            ssePublisher.publishBroadcast(author.getId(), SseEventType.NOTIFY_PUBLISHED, payload);

            JsonNode data = json(stream.next(SseEventType.NOTIFY_PUBLISHED.getValue(),
                    names(plannerId), DELIVERY_TIMEOUT));
            assertThat(data.path("plannerId").asString()).isEqualTo(plannerId);
            assertThat(data.has("excludeUserId")).isFalse();
        }
    }

    @Test
    void sseSuspensionCrossPod_WhenPublishedOnPrimary_DeliveredToSuspendedUser() {
        User suspended = user("sse-suspended");

        try (SseStream stream = SseStream.user(port, session(suspended))) {
            ssePublisher.publishAccountSuspended(suspended.getId(), "spam", SuspensionType.BAN, null);

            JsonNode data = json(stream.next(SseEventType.ACCOUNT_SUSPENDED.getValue(), DELIVERY_TIMEOUT));
            assertThat(data.path("suspensionType").asString()).isEqualTo("BAN");
        }
    }

    @Test
    void settingsInvalidate_WhenPublishedOnPrimary_InvalidatesLocalSettingsCache() {
        User user = user("sse-settings");
        User author = user("sse-settings-author");
        String plannerId = "planner-settings-" + UUID.randomUUID();

        try (SseStream stream = SseStream.user(port, session(user))) {
            enableNewPublicationNotices(user);

            ssePublisher.publishSettingsInvalidation(user.getId());

            boolean delivered = false;
            for (int attempt = 0; attempt < SETTINGS_REPUBLISH_ATTEMPTS && !delivered; attempt++) {
                ssePublisher.publishBroadcast(author.getId(), SseEventType.NOTIFY_PUBLISHED,
                        Map.of("plannerId", plannerId, "plannerTitle", "Settings probe"));
                try {
                    stream.next(SseEventType.NOTIFY_PUBLISHED.getValue(), names(plannerId), SETTINGS_DELIVERY_WAIT);
                    delivered = true;
                } catch (AssertionError stillCached) {
                    delivered = false;
                }
            }

            assertThat(delivered)
                    .as("the stream caches the preferences it registered with, so a publication notice "
                            + "reaches it only once the invalidation has dropped that cache")
                    .isTrue();
        }
    }

    @Test
    void commentFanout_WhenPublishedOnPrimary_DeliveredToCommentSubscribersWithPayload() {
        User owner = user("sse-comment-fanout-owner");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        String commentId = "comment-" + UUID.randomUUID();
        Map<String, Object> payload = Map.of("commentId", commentId, "body", "nice deck");

        try (SseStream stream = SseStream.comments(port, planner.getId(), session(owner))) {
            ssePublisher.publishCommentEvent(planner.getId(), SseEventType.COMMENT_ADDED, commentId, null, payload);

            assertThat(stream.next(SseEventType.COMMENT_ADDED.getValue(), DELIVERY_TIMEOUT)).contains(commentId);
        }
    }

    @Test
    void createComment_WhenWritePathRuns_FansOutToEverySubscriberButTheCommenter() {
        User owner = user("sse-comment-owner");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        User commenter = user("sse-comment");
        User markerAuthor = user("sse-comment-marker");
        UUID plannerId = planner.getId();

        try (SseStream commenterStream = SseStream.comments(port, plannerId, session(commenter));
             SseStream stream = SseStream.comments(port, plannerId, session(owner))) {
            CreateCommentResponse own = commentCommandService.createComment(
                    plannerId,
                    commenter.getId(),
                    UUID.randomUUID(),
                    new CreateCommentRequest("Great deck, thanks for sharing", null));

            assertThat(stream.next(SseEventType.COMMENT_ADDED.getValue(), entity(own.id()), DELIVERY_TIMEOUT))
                    .contains(plannerId.toString());

            CreateCommentResponse marker = commentCommandService.createComment(
                    plannerId,
                    markerAuthor.getId(),
                    UUID.randomUUID(),
                    new CreateCommentRequest("A later comment from someone else", null));

            assertThat(json(commenterStream.next(SseEventType.COMMENT_ADDED.getValue(), DELIVERY_TIMEOUT))
                    .path("entityId").asString())
                    .as("a planner's subscribers are sent to in registration order, and the commenter's "
                            + "stream registered before the owner's, so its own comment would precede this one")
                    .isEqualTo(marker.id().toString());
        }
    }

    private User user(String prefix) {
        return TestDataFactory.createTestUser(userRepository, prefix + "-" + UUID.randomUUID() + "@example.com");
    }

    private Cookie[] session(User user) {
        return AuthCookies.session(TestDataFactory.generateAccessToken(jwtTokenService, user), UUID.randomUUID());
    }

    private void enableNewPublicationNotices(User user) {
        userSettingsService.updateSettings(user.getId(), new UpdateUserSettingsRequest(null, null, null, true));
    }

    private Predicate<String> entity(UUID entityId) {
        return data -> entityId.toString().equals(json(data).path("entityId").asString());
    }

    private Predicate<String> names(String plannerId) {
        return data -> plannerId.equals(json(data).path("plannerId").asString());
    }

    private JsonNode json(String data) {
        try {
            return objectMapper.readTree(data);
        } catch (JacksonException e) {
            throw new AssertionError("the frame data is not JSON: " + data, e);
        }
    }
}

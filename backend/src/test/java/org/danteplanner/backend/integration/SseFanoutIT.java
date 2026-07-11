package org.danteplanner.backend.integration;

import java.util.Map;
import java.util.UUID;
import org.danteplanner.backend.comment.service.PlannerCommentSseService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SseService;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Phase-10 acceptance test: cross-node SSE fan-out over Redis pub/sub.
 *
 * <p>Models a Seoul-style pod that publishes to the Oregon Redis <b>primary</b> yet subscribes to
 * its <b>local</b> Redis: a "planner updated" event published on the primary via {@link SsePublisher}
 * must reach the subscriber listening on the local endpoint and be dispatched to this node's
 * {@link SseService} carrying the payload — true fan-out with no sticky sessions, and no
 * notify-then-refetch (the recipient patches its cache from the payload).</p>
 *
 * <p>The harness reuses {@link CausalHarnessSupport}'s full app context (MySQL primary/replica +
 * {@code AUTH_REDIS}). The publisher targets the primary (bound to {@code redis.auth.*}); the
 * subscriber binds to the distinct {@code redis.sse-local.*} endpoint. Both point at the same
 * {@code AUTH_REDIS} container: the code contract (publish via the primary factory, subscribe via a
 * distinct sse-local factory, fan out to local emitters) is proven; replication propagation is a
 * Redis deployment guarantee, out of scope. Async delivery is captured with a Mockito
 * {@code timeout(...)} verification on a {@link MockitoSpyBean} {@link SseService} — no fixed sleeps
 * (INV4).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class SseFanoutIT extends CausalHarnessSupport {

    @DynamicPropertySource
    static void sseLocalRedis(DynamicPropertyRegistry registry) {
        registry.add("redis.sse-local.host", AUTH_REDIS::getRedisHost);
        registry.add("redis.sse-local.port", AUTH_REDIS::getRedisPort);
    }

    @Autowired
    private SsePublisher ssePublisher;

    @MockitoSpyBean
    private SseService sseService;

    @MockitoSpyBean
    private PlannerCommentSseService plannerCommentSseService;

    @Test
    @DisplayName("Event published on the primary Redis is delivered to a local emitter with its payload")
    void fanout_WhenPublishedOnPrimary_DeliveredToLocalEmitterWithPayload() {
        Long userId = 4242L;
        String entityId = "planner-9";
        Map<String, Object> payload = Map.of("plannerId", entityId, "title", "Refactored deck");

        ssePublisher.publishUserEvent(userId, SseEventType.UPDATED, entityId, payload);

        verify(sseService, timeout(5000)).sendToUser(
                eq(userId),
                eq(SseEventType.UPDATED.getValue()),
                argThat(data -> data != null && data.toString().contains(entityId)));
    }

    @Test
    @DisplayName("Settings-cache invalidation published on the primary Redis invalidates the local cache")
    void settingsInvalidate_WhenPublishedOnPrimary_InvalidatesLocalSettingsCache() {
        Long userId = 7777L;

        ssePublisher.publishSettingsInvalidation(userId);

        verify(sseService, timeout(5000)).invalidateSettingsCache(eq(userId));
    }

    @Test
    @DisplayName("Comment event published on the primary Redis is delivered to comment subscribers with its payload")
    void commentFanout_WhenPublishedOnPrimary_DeliveredToCommentSubscribersWithPayload() {
        UUID plannerId = UUID.randomUUID();
        String commentId = "comment-123";
        Map<String, Object> payload = Map.of("commentId", commentId, "body", "nice deck");

        ssePublisher.publishCommentEvent(plannerId, SseEventType.CREATED, commentId, payload);

        verify(plannerCommentSseService, timeout(5000)).broadcast(
                eq(plannerId),
                eq(SseEventType.CREATED.getValue()),
                argThat(data -> data != null && data.toString().contains(commentId)));
    }
}

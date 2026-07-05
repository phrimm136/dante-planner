package org.danteplanner.backend.service;
import org.danteplanner.backend.comment.service.PlannerCommentSseService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Characterization tests pinning the observable emitter-lifecycle behavior of
 * {@link PlannerCommentSseService}: subscriber registration, the per-planner capacity
 * cap, dead-emitter removal on broadcast/heartbeat/cleanup, removal idempotency, and the
 * JSON-serialization error path.
 *
 * <p>These tests exercise only the public API. A dead connection is simulated by calling
 * {@link SseEmitter#complete()} on the returned emitter; the next {@code send} then throws
 * {@link IllegalStateException}, which the service catches and treats as a dead
 * connection (identical to the production {@code IOException} path).</p>
 */
class PlannerCommentSseServiceTest {

    /** Mirrors {@code PlannerCommentSseService.MAX_CONNECTIONS_PER_PLANNER}. */
    private static final int MAX_CONNECTIONS_PER_PLANNER = 500;

    private ObjectMapper objectMapper;
    private PlannerCommentSseService service;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new PlannerCommentSseService(objectMapper);
        plannerId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("subscribe")
    class Subscribe {

        @Test
        @DisplayName("registers a subscriber for the planner")
        void subscribe_WhenNewDevice_RegistersSubscriber() {
            service.subscribe(plannerId, UUID.randomUUID());

            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);
        }

        @Test
        @DisplayName("replaces the prior emitter when the same device reconnects")
        void subscribe_WhenSameDeviceReconnects_DoesNotDuplicate() {
            UUID deviceId = UUID.randomUUID();

            service.subscribe(plannerId, deviceId);
            service.subscribe(plannerId, deviceId);

            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);
        }

        @Test
        @DisplayName("caps the subscriber count by evicting the oldest at capacity")
        void subscribe_WhenAtCapacity_CapsCount() {
            for (int i = 0; i < MAX_CONNECTIONS_PER_PLANNER + 1; i++) {
                service.subscribe(plannerId, UUID.randomUUID());
            }

            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(MAX_CONNECTIONS_PER_PLANNER);
        }
    }

    @Nested
    @DisplayName("broadcastCommentAdded")
    class BroadcastCommentAdded {

        @Test
        @DisplayName("removes the connection when the emitter is dead")
        void broadcastCommentAdded_WhenEmitterDead_RemovesConnection() {
            UUID deviceId = UUID.randomUUID();
            SseEmitter emitter = service.subscribe(plannerId, deviceId);
            emitter.complete();
            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);

            service.broadcastCommentAdded(plannerId, null);

            assertThat(service.getSubscriberCount(plannerId)).isZero();
        }

        @Test
        @DisplayName("does nothing when the planner has no subscribers")
        void broadcastCommentAdded_WhenNoSubscribers_DoesNothing() {
            assertThatCode(() -> service.broadcastCommentAdded(plannerId, null))
                    .doesNotThrowAnyException();
            assertThat(service.getSubscriberCount(plannerId)).isZero();
        }

        @Test
        @DisplayName("does not propagate when serialization fails")
        void broadcastCommentAdded_WhenSerializationFails_DoesNotThrow() throws Exception {
            ObjectMapper failingMapper = mock(ObjectMapper.class);
            when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});
            PlannerCommentSseService failingService = new PlannerCommentSseService(failingMapper);
            failingService.subscribe(plannerId, UUID.randomUUID());

            assertThatCode(() -> failingService.broadcastCommentAdded(plannerId, null))
                    .doesNotThrowAnyException();
            assertThat(failingService.getSubscriberCount(plannerId)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("sendHeartbeats")
    class SendHeartbeats {

        @Test
        @DisplayName("removes the connection when the emitter is dead")
        void sendHeartbeats_WhenEmitterDead_RemovesConnection() {
            SseEmitter emitter = service.subscribe(plannerId, UUID.randomUUID());
            emitter.complete();
            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);

            service.sendHeartbeats();

            assertThat(service.getSubscriberCount(plannerId)).isZero();
        }

        @Test
        @DisplayName("keeps a live connection")
        void sendHeartbeats_WhenEmitterLive_KeepsConnection() {
            service.subscribe(plannerId, UUID.randomUUID());

            service.sendHeartbeats();

            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("cleanupZombieConnections")
    class CleanupZombieConnections {

        @Test
        @DisplayName("removes the connection when the probe fails")
        void cleanupZombieConnections_WhenEmitterDead_RemovesConnection() {
            SseEmitter emitter = service.subscribe(plannerId, UUID.randomUUID());
            emitter.complete();
            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);

            service.cleanupZombieConnections();

            assertThat(service.getSubscriberCount(plannerId)).isZero();
        }

        @Test
        @DisplayName("keeps a live connection")
        void cleanupZombieConnections_WhenEmitterLive_KeepsConnection() {
            service.subscribe(plannerId, UUID.randomUUID());

            service.cleanupZombieConnections();

            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("removeConnection")
    class RemoveConnection {

        @Test
        @DisplayName("is idempotent when called twice for the same device")
        void removeConnection_WhenCalledTwice_DoesNotThrow() {
            UUID deviceId = UUID.randomUUID();
            service.subscribe(plannerId, deviceId);

            service.removeConnection(plannerId, deviceId);
            assertThat(service.getSubscriberCount(plannerId)).isZero();

            assertThatCode(() -> service.removeConnection(plannerId, deviceId))
                    .doesNotThrowAnyException();
            assertThat(service.getSubscriberCount(plannerId)).isZero();
        }
    }

    @Nested
    @DisplayName("getTotalConnectionCount")
    class GetTotalConnectionCount {

        @Test
        @DisplayName("sums subscribers across all planners")
        void getTotalConnectionCount_WhenMultiplePlanners_SumsAll() {
            service.subscribe(plannerId, UUID.randomUUID());
            service.subscribe(UUID.randomUUID(), UUID.randomUUID());

            assertThat(service.getTotalConnectionCount()).isEqualTo(2);
        }
    }
}

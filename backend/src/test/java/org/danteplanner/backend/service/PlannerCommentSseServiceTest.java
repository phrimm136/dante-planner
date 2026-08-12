package org.danteplanner.backend.service;
import org.danteplanner.backend.comment.service.PlannerCommentSseService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.shared.sse.SseCapacityExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private PlannerAccessGuard plannerAccessGuard;
    private PlannerCommentSseService service;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        plannerAccessGuard = mock(PlannerAccessGuard.class);
        service = new PlannerCommentSseService(objectMapper, plannerAccessGuard);
        plannerId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("subscribe")
    class Subscribe {

        @Test
        @DisplayName("registers a subscriber for the planner")
        void subscribe_WhenNewDevice_RegistersSubscriber() throws IOException {
            service.subscribe(plannerId, UUID.randomUUID(), null);

            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);
        }

        @Test
        @DisplayName("replaces the prior emitter when the same device reconnects")
        void subscribe_WhenSameDeviceReconnects_DoesNotDuplicate() throws IOException {
            UUID deviceId = UUID.randomUUID();

            service.subscribe(plannerId, deviceId, null);
            service.subscribe(plannerId, deviceId, null);

            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);
        }

        @Test
        @DisplayName("rejects the arriving subscriber at capacity")
        void subscribe_WhenAtCapacity_RejectsTheNewcomer() throws IOException {
            fillToCapacity();

            assertThatThrownBy(() -> service.subscribe(plannerId, UUID.randomUUID(), null))
                    .isInstanceOf(SseCapacityExceededException.class);
        }

        @Test
        @DisplayName("keeps every connected subscriber when one is rejected at capacity")
        void subscribe_WhenAtCapacity_KeepsTheConnectedSubscribers() throws IOException {
            fillToCapacity();

            assertThatThrownBy(() -> service.subscribe(plannerId, UUID.randomUUID(), null))
                    .isInstanceOf(SseCapacityExceededException.class);

            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(MAX_CONNECTIONS_PER_PLANNER);
            // A closed emitter throws on the next send and is dropped, so a count that survives a
            // heartbeat round is what proves no connected subscriber's stream was completed.
            service.sendHeartbeats();
            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(MAX_CONNECTIONS_PER_PLANNER);
        }

        @Test
        @DisplayName("admits a connected device's own reconnect at capacity")
        void subscribe_WhenSameDeviceReconnectsAtCapacity_IsAdmitted() throws IOException {
            UUID deviceId = UUID.randomUUID();
            service.subscribe(plannerId, deviceId, null);
            for (int i = 0; i < MAX_CONNECTIONS_PER_PLANNER - 1; i++) {
                service.subscribe(plannerId, UUID.randomUUID(), null);
            }

            assertThatCode(() -> service.subscribe(plannerId, deviceId, null))
                    .doesNotThrowAnyException();
            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(MAX_CONNECTIONS_PER_PLANNER);
        }

        private void fillToCapacity() throws IOException {
            for (int i = 0; i < MAX_CONNECTIONS_PER_PLANNER; i++) {
                service.subscribe(plannerId, UUID.randomUUID(), null);
            }
        }
    }

    @Nested
    @DisplayName("broadcastCommentAdded")
    class BroadcastCommentAdded {

        @Test
        @DisplayName("removes the connection when the emitter is dead")
        void broadcast_WhenEmitterDead_RemovesConnection() throws IOException {
            UUID deviceId = UUID.randomUUID();
            SseEmitter emitter = service.subscribe(plannerId, deviceId, null);
            emitter.complete();
            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);

            service.broadcast(plannerId, "comment:added", Map.of("id", UUID.randomUUID().toString()), null);

            assertThat(service.getSubscriberCount(plannerId)).isZero();
        }

        @Test
        @DisplayName("does nothing when the planner has no subscribers")
        void broadcast_WhenNoSubscribers_DoesNothing() {
            assertThatCode(() -> service.broadcast(plannerId, "comment:added", Map.of("id", UUID.randomUUID().toString()), null))
                    .doesNotThrowAnyException();
            assertThat(service.getSubscriberCount(plannerId)).isZero();
        }

        @Test
        @DisplayName("does not propagate when serialization fails")
        void broadcast_WhenSerializationFails_DoesNotThrow() throws Exception {
            ObjectMapper failingMapper = mock(ObjectMapper.class);
            when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});
            PlannerCommentSseService failingService =
                    new PlannerCommentSseService(failingMapper, plannerAccessGuard);
            failingService.subscribe(plannerId, UUID.randomUUID(), null);

            assertThatCode(() -> failingService.broadcast(plannerId, "comment:added", Map.of("id", UUID.randomUUID().toString()), null))
                    .doesNotThrowAnyException();
            assertThat(failingService.getSubscriberCount(plannerId)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("sendHeartbeats")
    class SendHeartbeats {

        @Test
        @DisplayName("removes the connection when the emitter is dead")
        void sendHeartbeats_WhenEmitterDead_RemovesConnection() throws IOException {
            SseEmitter emitter = service.subscribe(plannerId, UUID.randomUUID(), null);
            emitter.complete();
            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);

            service.sendHeartbeats();

            assertThat(service.getSubscriberCount(plannerId)).isZero();
        }

        @Test
        @DisplayName("keeps a live connection")
        void sendHeartbeats_WhenEmitterLive_KeepsConnection() throws IOException {
            service.subscribe(plannerId, UUID.randomUUID(), null);

            service.sendHeartbeats();

            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("cleanupZombieConnections")
    class CleanupZombieConnections {

        @Test
        @DisplayName("removes the connection when the probe fails")
        void cleanupZombieConnections_WhenEmitterDead_RemovesConnection() throws IOException {
            SseEmitter emitter = service.subscribe(plannerId, UUID.randomUUID(), null);
            emitter.complete();
            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);

            service.cleanupZombieConnections();

            assertThat(service.getSubscriberCount(plannerId)).isZero();
        }

        @Test
        @DisplayName("keeps a live connection")
        void cleanupZombieConnections_WhenEmitterLive_KeepsConnection() throws IOException {
            service.subscribe(plannerId, UUID.randomUUID(), null);

            service.cleanupZombieConnections();

            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("removeConnection")
    class RemoveConnection {

        @Test
        @DisplayName("is idempotent when called twice for the same device")
        void removeConnection_WhenCalledTwice_DoesNotThrow() throws IOException {
            UUID deviceId = UUID.randomUUID();
            service.subscribe(plannerId, deviceId, null);

            service.removeConnection(plannerId, deviceId);
            assertThat(service.getSubscriberCount(plannerId)).isZero();

            assertThatCode(() -> service.removeConnection(plannerId, deviceId))
                    .doesNotThrowAnyException();
            assertThat(service.getSubscriberCount(plannerId)).isZero();
        }
    }

    @Nested
    @DisplayName("author exclusion")
    class AuthorExclusion {

        @Test
        @DisplayName("skips every connection of the excluded account and keeps the rest")
        void broadcast_WhenExcludingAuthor_SkipsTheirConnectionsOnly() throws IOException {
            Long author = 7L;
            SseEmitter authorTab = service.subscribe(plannerId, UUID.randomUUID(), author);
            SseEmitter authorOtherTab = service.subscribe(plannerId, UUID.randomUUID(), author);
            SseEmitter reader = service.subscribe(plannerId, UUID.randomUUID(), 8L);
            SseEmitter guest = service.subscribe(plannerId, UUID.randomUUID(), null);
            // A completed emitter throws on send, which the service treats as a dead connection
            // and removes; the survivors are therefore the ones that were skipped.
            authorTab.complete();
            authorOtherTab.complete();
            reader.complete();
            guest.complete();

            service.broadcast(plannerId, "comment:added", Map.of("id", "c1"), author);

            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getTotalConnectionCount")
    class GetTotalConnectionCount {

        @Test
        @DisplayName("sums subscribers across all planners")
        void getTotalConnectionCount_WhenMultiplePlanners_SumsAll() throws IOException {
            service.subscribe(plannerId, UUID.randomUUID(), null);
            service.subscribe(UUID.randomUUID(), UUID.randomUUID(), null);

            assertThat(service.getTotalConnectionCount()).isEqualTo(2);
        }
    }
}

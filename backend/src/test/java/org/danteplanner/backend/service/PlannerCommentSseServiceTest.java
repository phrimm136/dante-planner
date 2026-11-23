package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.api.PlannerThread;
import java.util.Optional;
import org.danteplanner.backend.comment.service.PlannerCommentSseService;

import org.danteplanner.backend.shared.security.Viewer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.api.PlannerAccess;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.shared.sse.SseCapacityExceededException;
import org.danteplanner.backend.shared.sse.SseEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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
@ExtendWith(MockitoExtension.class)
class PlannerCommentSseServiceTest {

    private static final Viewer GUEST = new Viewer.Guest();

    private static final long PLANNER_OWNER_ID = 41L;

    /** Mirrors {@code PlannerCommentSseService.MAX_CONNECTIONS_PER_PLANNER}. */
    private static final int MAX_CONNECTIONS_PER_PLANNER = 500;

    /** Runs each submitted heartbeat on the calling thread, so a sweep's effects hold on return. */
    private static final TaskExecutor SAME_THREAD = Runnable::run;

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private ObjectMapper failingMapper;

    private ObjectMapper objectMapper;
    private PlannerAccess plannerAccessGuard;
    private PlannerCommentSseService service;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        plannerAccessGuard = new PlannerAccessGuard(null, plannerRepository);
        service = new PlannerCommentSseService(objectMapper, plannerAccessGuard, SAME_THREAD);
        plannerId = UUID.randomUUID();
        lenient().when(plannerRepository.findThread(any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(
                        new PlannerThread(invocation.getArgument(0), PLANNER_OWNER_ID, true, false)));
    }

    @Nested
    class Subscribe {

        @Test
        void subscribe_WhenNewDevice_RegistersSubscriber() throws IOException {
            service.subscribe(plannerId, UUID.randomUUID(), GUEST);

            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);
        }

        @Test
        void subscribe_WhenSameDeviceReconnects_DoesNotDuplicate() throws IOException {
            UUID deviceId = UUID.randomUUID();

            service.subscribe(plannerId, deviceId, GUEST);
            service.subscribe(plannerId, deviceId, GUEST);

            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);
        }

        @Test
        void subscribe_WhenAtCapacity_RejectsTheNewcomer() throws IOException {
            fillToCapacity();

            assertThatThrownBy(() -> service.subscribe(plannerId, UUID.randomUUID(), GUEST))
                    .isInstanceOf(SseCapacityExceededException.class)
                    .hasMessageStartingWith("SSE connection limit reached for planner " + plannerId);
        }

        @Test
        void subscribe_WhenAtCapacity_KeepsTheConnectedSubscribers() throws IOException {
            fillToCapacity();

            assertThatThrownBy(() -> service.subscribe(plannerId, UUID.randomUUID(), GUEST))
                    .isInstanceOf(SseCapacityExceededException.class)
                    .hasMessageStartingWith("SSE connection limit reached for planner " + plannerId);

            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(MAX_CONNECTIONS_PER_PLANNER);
            // A closed emitter throws on the next send and is dropped, so a count that survives a
            // heartbeat round is what proves no connected subscriber's stream was completed.
            service.sweepSseHeartbeats();
            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(MAX_CONNECTIONS_PER_PLANNER);
        }

        @Test
        void subscribe_WhenSameDeviceReconnectsAtCapacity_IsAdmitted() throws IOException {
            UUID deviceId = UUID.randomUUID();
            service.subscribe(plannerId, deviceId, GUEST);
            for (int i = 0; i < MAX_CONNECTIONS_PER_PLANNER - 1; i++) {
                service.subscribe(plannerId, UUID.randomUUID(), GUEST);
            }

            assertThatCode(() -> service.subscribe(plannerId, deviceId, GUEST))
                    .doesNotThrowAnyException();
            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(MAX_CONNECTIONS_PER_PLANNER);
        }

        private void fillToCapacity() throws IOException {
            for (int i = 0; i < MAX_CONNECTIONS_PER_PLANNER; i++) {
                service.subscribe(plannerId, UUID.randomUUID(), GUEST);
            }
        }
    }

    @Nested
    class BroadcastCommentAdded {

        @Test
        void broadcast_WhenEmitterDead_RemovesConnection() throws IOException {
            UUID deviceId = UUID.randomUUID();
            SseEmitter emitter = service.subscribe(plannerId, deviceId, GUEST);
            emitter.complete();
            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);

            service.broadcast(plannerId, "comment:added", Map.of("id", UUID.randomUUID().toString()), new SseEnvelope.UserExclusion.None());

            assertThat(service.getSubscriberCount(plannerId)).isZero();
        }

        @Test
        void broadcast_WhenNoSubscribers_DoesNothing() {
            assertThatCode(() -> service.broadcast(plannerId, "comment:added", Map.of("id", UUID.randomUUID().toString()), new SseEnvelope.UserExclusion.None()))
                    .doesNotThrowAnyException();
            assertThat(service.getSubscriberCount(plannerId)).isZero();
        }

        @Test
        void broadcast_WhenSerializationFails_DoesNotThrow() throws Exception {
            when(failingMapper.writeValueAsString(any())).thenThrow(new JacksonException("boom") {});
            PlannerCommentSseService failingService =
                    new PlannerCommentSseService(failingMapper, plannerAccessGuard, SAME_THREAD);
            failingService.subscribe(plannerId, UUID.randomUUID(), GUEST);

            assertThatCode(() -> failingService.broadcast(plannerId, "comment:added", Map.of("id", UUID.randomUUID().toString()), new SseEnvelope.UserExclusion.None()))
                    .doesNotThrowAnyException();
            assertThat(failingService.getSubscriberCount(plannerId)).isEqualTo(1);
        }
    }

    @Nested
    class SweepSseHeartbeats {

        @Test
        void sweepSseHeartbeats_WhenEmitterDead_RemovesConnection() throws IOException {
            SseEmitter emitter = service.subscribe(plannerId, UUID.randomUUID(), GUEST);
            emitter.complete();
            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);

            service.sweepSseHeartbeats();

            assertThat(service.getSubscriberCount(plannerId)).isZero();
        }

        @Test
        void sweepSseHeartbeats_WhenEmitterLive_KeepsConnection() throws IOException {
            service.subscribe(plannerId, UUID.randomUUID(), GUEST);

            service.sweepSseHeartbeats();

            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);
        }
    }

    @Nested
    class CleanupZombieConnections {

        @Test
        void cleanupZombieConnections_WhenEmitterDead_RemovesConnection() throws IOException {
            SseEmitter emitter = service.subscribe(plannerId, UUID.randomUUID(), GUEST);
            emitter.complete();
            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);

            service.cleanupZombieConnections();

            assertThat(service.getSubscriberCount(plannerId)).isZero();
        }

        @Test
        void cleanupZombieConnections_WhenEmitterLive_KeepsConnection() throws IOException {
            service.subscribe(plannerId, UUID.randomUUID(), GUEST);

            service.cleanupZombieConnections();

            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(1);
        }
    }

    @Nested
    class RemoveConnection {

        @Test
        void removeConnection_WhenCalledTwice_DoesNotThrow() throws IOException {
            UUID deviceId = UUID.randomUUID();
            service.subscribe(plannerId, deviceId, GUEST);

            service.removeConnection(plannerId, deviceId);
            assertThat(service.getSubscriberCount(plannerId)).isZero();

            assertThatCode(() -> service.removeConnection(plannerId, deviceId))
                    .doesNotThrowAnyException();
            assertThat(service.getSubscriberCount(plannerId)).isZero();
        }
    }

    @Nested
    class AuthorExclusion {

        @Test
        void broadcast_WhenExcludingAuthor_SkipsTheirConnectionsOnly() throws IOException {
            Long author = 7L;
            SseEmitter authorTab = service.subscribe(plannerId, UUID.randomUUID(), new Viewer.Member(author));
            SseEmitter authorOtherTab = service.subscribe(plannerId, UUID.randomUUID(), new Viewer.Member(author));
            SseEmitter reader = service.subscribe(plannerId, UUID.randomUUID(), new Viewer.Member(8L));
            SseEmitter guest = service.subscribe(plannerId, UUID.randomUUID(), GUEST);
            // A completed emitter throws on send, which the service treats as a dead connection
            // and removes; the survivors are therefore the ones that were skipped.
            authorTab.complete();
            authorOtherTab.complete();
            reader.complete();
            guest.complete();

            service.broadcast(plannerId, "comment:added", Map.of("id", "c1"), new SseEnvelope.UserExclusion.User(author));

            assertThat(service.getSubscriberCount(plannerId)).isEqualTo(2);
        }
    }

    @Nested
    class GetTotalConnectionCount {

        @Test
        void getTotalConnectionCount_WhenMultiplePlanners_SumsAll() throws IOException {
            service.subscribe(plannerId, UUID.randomUUID(), GUEST);
            service.subscribe(UUID.randomUUID(), UUID.randomUUID(), GUEST);

            assertThat(service.getTotalConnectionCount()).isEqualTo(2);
        }
    }
}

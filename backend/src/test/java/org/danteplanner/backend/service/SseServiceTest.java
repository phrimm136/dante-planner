package org.danteplanner.backend.service;
import org.danteplanner.backend.shared.sse.SseService;
import org.danteplanner.backend.user.service.UserSettingsService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.user.entity.UserSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterization tests pinning the observable emitter-lifecycle behavior of
 * {@link SseService}: connection registration, dead-emitter removal on send/heartbeat/
 * cleanup, removal idempotency, and the JSON-serialization error path.
 *
 * <p>These tests exercise only the public API. A dead connection is simulated by
 * calling {@link SseEmitter#complete()} on the returned emitter; the next {@code send}
 * then throws {@link IllegalStateException}, which the service catches and treats as a
 * dead connection (identical to the production {@code IOException} path).</p>
 */
@ExtendWith(MockitoExtension.class)
class SseServiceTest {

    private static final Long USER_ID = 1L;

    /** Runs each submitted heartbeat on the calling thread, so a sweep's effects hold on return. */
    private static final TaskExecutor SAME_THREAD = Runnable::run;

    @Mock
    private UserSettingsService userSettingsService;

    private ObjectMapper objectMapper;
    private SseService sseService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        sseService = new SseService(objectMapper, userSettingsService, SAME_THREAD);
        lenient().when(userSettingsService.getOrCreateEntity(anyLong())).thenReturn(allEnabledSettings());
    }

    private UserSettings allEnabledSettings() {
        return UserSettings.builder()
                .userId(USER_ID)
                .syncEnabled(true)
                .notifyComments(true)
                .notifyRecommendations(true)
                .notifyNewPublications(true)
                .build();
    }

    @Nested
    @DisplayName("subscribe")
    class Subscribe {

        @Test
        @DisplayName("registers a connection for the user")
        void subscribe_WhenNewDevice_RegistersConnection() throws IOException {
            sseService.subscribe(USER_ID, UUID.randomUUID());

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(1);
        }

        @Test
        @DisplayName("replaces the prior emitter when the same device reconnects")
        void subscribe_WhenSameDeviceReconnects_DoesNotDuplicate() throws IOException {
            UUID deviceId = UUID.randomUUID();

            sseService.subscribe(USER_ID, deviceId);
            sseService.subscribe(USER_ID, deviceId);

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(1);
        }

        @Test
        @DisplayName("tracks each distinct device separately")
        void subscribe_WhenMultipleDevices_CountsAll() throws IOException {
            sseService.subscribe(USER_ID, UUID.randomUUID());
            sseService.subscribe(USER_ID, UUID.randomUUID());

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("sendToUser")
    class SendToUser {

        @Test
        @DisplayName("removes the connection when the emitter is dead")
        void sendToUser_WhenEmitterDead_RemovesConnection() throws IOException {
            UUID deviceId = UUID.randomUUID();
            SseEmitter emitter = sseService.subscribe(USER_ID, deviceId);
            emitter.complete();
            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(1);

            sseService.sendToUser(USER_ID, "notify:comment", Map.of("k", "v"));

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isZero();
        }

        @Test
        @DisplayName("does not propagate or remove when serialization fails")
        void sendToUser_WhenSerializationFails_DoesNotThrowNorRemove() throws Exception {
            ObjectMapper failingMapper = mock(ObjectMapper.class);
            when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});
            SseService service = new SseService(failingMapper, userSettingsService, SAME_THREAD);
            service.subscribe(USER_ID, UUID.randomUUID());

            assertThatCode(() -> service.sendToUser(USER_ID, "notify:comment", Map.of("k", "v")))
                    .doesNotThrowAnyException();
            assertThat(service.getActiveConnectionCount(USER_ID)).isEqualTo(1);
        }

        @Test
        @DisplayName("does not deliver an event type this node does not recognize")
        void sendToUser_WhenEventTypeUnrecognized_DoesNotDeliver() throws Exception {
            ObjectMapper watchedMapper = mock(ObjectMapper.class);
            SseService service = new SseService(watchedMapper, userSettingsService, SAME_THREAD);
            service.subscribe(USER_ID, UUID.randomUUID());

            service.sendToUser(USER_ID, "notify:something-this-node-cannot-name", Map.of("k", "v"));

            // Serialization is the first step of delivery, so never reaching it is what proves the
            // event was dropped rather than sent.
            verify(watchedMapper, never()).writeValueAsString(any());
            assertThat(service.getActiveConnectionCount(USER_ID)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("broadcastToAll")
    class BroadcastToAll {

        @Test
        @DisplayName("removes the connection when the emitter is dead")
        void broadcastToAll_WhenEmitterDead_RemovesConnection() throws IOException {
            UUID deviceId = UUID.randomUUID();
            SseEmitter emitter = sseService.subscribe(USER_ID, deviceId);
            emitter.complete();
            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(1);

            sseService.broadcastToAll(null, "notify:published", Map.of("k", "v"));

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isZero();
        }

        @Test
        @DisplayName("does not propagate when serialization fails")
        void broadcastToAll_WhenSerializationFails_DoesNotThrow() throws Exception {
            ObjectMapper failingMapper = mock(ObjectMapper.class);
            when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});
            SseService service = new SseService(failingMapper, userSettingsService, SAME_THREAD);
            service.subscribe(USER_ID, UUID.randomUUID());

            assertThatCode(() -> service.broadcastToAll(null, "notify:published", Map.of("k", "v")))
                    .doesNotThrowAnyException();
            assertThat(service.getActiveConnectionCount(USER_ID)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("sweepSseHeartbeats")
    class SendHeartbeats {

        @Test
        @DisplayName("removes the connection when the emitter is dead")
        void sweepSseHeartbeats_WhenEmitterDead_RemovesConnection() throws IOException {
            SseEmitter emitter = sseService.subscribe(USER_ID, UUID.randomUUID());
            emitter.complete();
            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(1);

            sseService.sweepSseHeartbeats();

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isZero();
        }

        @Test
        @DisplayName("keeps a live connection")
        void sweepSseHeartbeats_WhenEmitterLive_KeepsConnection() throws IOException {
            sseService.subscribe(USER_ID, UUID.randomUUID());

            sseService.sweepSseHeartbeats();

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("cleanupZombieConnections")
    class CleanupZombieConnections {

        @Test
        @DisplayName("removes the connection when the probe fails")
        void cleanupZombieConnections_WhenEmitterDead_RemovesConnection() throws IOException {
            SseEmitter emitter = sseService.subscribe(USER_ID, UUID.randomUUID());
            emitter.complete();
            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(1);

            sseService.cleanupZombieConnections();

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isZero();
        }

        @Test
        @DisplayName("keeps a live connection")
        void cleanupZombieConnections_WhenEmitterLive_KeepsConnection() throws IOException {
            sseService.subscribe(USER_ID, UUID.randomUUID());

            sseService.cleanupZombieConnections();

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("removeConnection")
    class RemoveConnection {

        @Test
        @DisplayName("is idempotent when called twice for the same device")
        void removeConnection_WhenCalledTwice_DoesNotThrow() throws IOException {
            UUID deviceId = UUID.randomUUID();
            sseService.subscribe(USER_ID, deviceId);

            sseService.removeConnection(USER_ID, deviceId);
            assertThat(sseService.getActiveConnectionCount(USER_ID)).isZero();

            assertThatCode(() -> sseService.removeConnection(USER_ID, deviceId))
                    .doesNotThrowAnyException();
            assertThat(sseService.getActiveConnectionCount(USER_ID)).isZero();
        }
    }
}

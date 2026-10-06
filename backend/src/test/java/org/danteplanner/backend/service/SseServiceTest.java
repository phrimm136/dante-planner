package org.danteplanner.backend.service;
import org.danteplanner.backend.shared.sse.SseService;
import org.danteplanner.backend.shared.sse.NotificationPreferenceProvider.Preferences;
import org.danteplanner.backend.support.FakeNotificationPreferences;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.UsersContract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
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
    private static final Long OTHER_USER_ID = 2L;

    /** Runs each submitted heartbeat on the calling thread, so a sweep's effects hold on return. */
    private static final TaskExecutor SAME_THREAD = Runnable::run;

    private final FakeUsers users = new FakeUsers();

    private final FakeNotificationPreferences preferenceProvider = new FakeNotificationPreferences(users);

    private ObjectMapper objectMapper;
    private SseService sseService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        sseService = new SseService(objectMapper, preferenceProvider, SAME_THREAD);
        users.add(UsersContract.newUser().id(USER_ID).build());
        users.add(UsersContract.newUser().id(OTHER_USER_ID).build());
        preferenceProvider.set(USER_ID, allEnabledSettings());
        preferenceProvider.set(OTHER_USER_ID, allEnabledSettings());
    }

    private Preferences allEnabledSettings() {
        return new Preferences(true, true, true);
    }

    @Nested
    class Subscribe {

        @Test
        void subscribe_WhenNewDevice_RegistersConnection() throws IOException {
            sseService.subscribe(USER_ID, UUID.randomUUID());

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(1);
        }

        @Test
        void subscribe_WhenSameDeviceReconnects_DoesNotDuplicate() throws IOException {
            UUID deviceId = UUID.randomUUID();

            sseService.subscribe(USER_ID, deviceId);
            sseService.subscribe(USER_ID, deviceId);

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(1);
        }

        @Test
        void subscribe_WhenMultipleDevices_CountsAll() throws IOException {
            sseService.subscribe(USER_ID, UUID.randomUUID());
            sseService.subscribe(USER_ID, UUID.randomUUID());

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(2);
        }
    }

    @Nested
    class SendToUser {

        @Test
        void sendToUser_WhenEmitterDead_RemovesConnection() throws IOException {
            UUID deviceId = UUID.randomUUID();
            SseEmitter emitter = sseService.subscribe(USER_ID, deviceId);
            emitter.complete();
            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(1);

            sseService.sendToUser(USER_ID, "notify:comment", Map.of("k", "v"));

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isZero();
        }

        @Test
        void sendToUser_WhenCommentNotificationsOff_DoesNotSend() throws IOException {
            preferenceProvider.set(USER_ID, new Preferences(false, true, true));
            SseEmitter emitter = sseService.subscribe(USER_ID, UUID.randomUUID());
            emitter.complete();

            sseService.sendToUser(USER_ID, "notify:comment", Map.of("k", "v"));

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(1);
        }

        @Test
        void sendToUser_WhenSerializationFails_DoesNotThrowNorRemove() throws Exception {
            ObjectMapper failingMapper = mock(ObjectMapper.class);
            when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});
            SseService service = new SseService(failingMapper, preferenceProvider, SAME_THREAD);
            service.subscribe(USER_ID, UUID.randomUUID());

            assertThatCode(() -> service.sendToUser(USER_ID, "notify:comment", Map.of("k", "v")))
                    .doesNotThrowAnyException();
            assertThat(service.getActiveConnectionCount(USER_ID)).isEqualTo(1);
        }

        @Test
        void sendToUser_WhenEventTypeUnrecognized_DoesNotDeliver() throws Exception {
            ObjectMapper watchedMapper = mock(ObjectMapper.class);
            SseService service = new SseService(watchedMapper, preferenceProvider, SAME_THREAD);
            service.subscribe(USER_ID, UUID.randomUUID());

            service.sendToUser(USER_ID, "notify:something-this-node-cannot-name", Map.of("k", "v"));

            // Serialization is the first step of delivery, so never reaching it is what proves the
            // event was dropped rather than sent.
            verify(watchedMapper, never()).writeValueAsString(any());
            assertThat(service.getActiveConnectionCount(USER_ID)).isEqualTo(1);
        }
    }

    @Nested
    class BroadcastToAll {

        @Test
        void broadcastToAll_WhenEmitterDead_RemovesConnection() throws IOException {
            UUID deviceId = UUID.randomUUID();
            SseEmitter emitter = sseService.subscribe(USER_ID, deviceId);
            emitter.complete();
            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(1);

            sseService.broadcastToAll(null, "notify:published", Map.of("k", "v"));

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isZero();
        }

        @Test
        void broadcastToAll_WhenAUserIsExcluded_SkipsOnlyThatUsersEmitters() throws IOException {
            Long excludedUser = USER_ID;
            Long otherUser = OTHER_USER_ID;
            sseService.subscribe(excludedUser, UUID.randomUUID()).complete();
            sseService.subscribe(otherUser, UUID.randomUUID()).complete();

            sseService.broadcastToAll(excludedUser, "notify:published", Map.of("k", "v"));

            assertThat(sseService.getActiveConnectionCount(excludedUser)).isEqualTo(1);
            assertThat(sseService.getActiveConnectionCount(otherUser)).isZero();
        }

        @Test
        void broadcastToAll_WhenSerializationFails_DoesNotThrow() throws Exception {
            ObjectMapper failingMapper = mock(ObjectMapper.class);
            when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});
            SseService service = new SseService(failingMapper, preferenceProvider, SAME_THREAD);
            service.subscribe(USER_ID, UUID.randomUUID());

            assertThatCode(() -> service.broadcastToAll(null, "notify:published", Map.of("k", "v")))
                    .doesNotThrowAnyException();
            assertThat(service.getActiveConnectionCount(USER_ID)).isEqualTo(1);
        }
    }

    @Nested
    class SweepSseHeartbeats {

        @Test
        void sweepSseHeartbeats_WhenEmitterDead_RemovesConnection() throws IOException {
            SseEmitter emitter = sseService.subscribe(USER_ID, UUID.randomUUID());
            emitter.complete();
            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(1);

            sseService.sweepSseHeartbeats();

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isZero();
        }

        @Test
        void sweepSseHeartbeats_WhenEmitterLive_KeepsConnection() throws IOException {
            sseService.subscribe(USER_ID, UUID.randomUUID());

            sseService.sweepSseHeartbeats();

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(1);
        }
    }

    @Nested
    class CleanupZombieConnections {

        @Test
        void cleanupZombieConnections_WhenEmitterDead_RemovesConnection() throws IOException {
            SseEmitter emitter = sseService.subscribe(USER_ID, UUID.randomUUID());
            emitter.complete();
            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(1);

            sseService.cleanupZombieConnections();

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isZero();
        }

        @Test
        void cleanupZombieConnections_WhenEmitterLive_KeepsConnection() throws IOException {
            sseService.subscribe(USER_ID, UUID.randomUUID());

            sseService.cleanupZombieConnections();

            assertThat(sseService.getActiveConnectionCount(USER_ID)).isEqualTo(1);
        }
    }

    @Nested
    class RemoveConnection {

        @Test
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

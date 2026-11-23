package org.danteplanner.backend.shared.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.redis.connection.DefaultMessage;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.ObjectMapper;
import org.danteplanner.backend.support.TestJsonMappers;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.entity.SseEventType;

class SseRedisSubscriberTest {

    private final ObjectMapper objectMapper = TestJsonMappers.springDefaults().rebuild()
            .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();
    private final RecordingSseService sseService = new RecordingSseService(objectMapper);
    private final SseRedisSubscriber subscriber =
            new SseRedisSubscriber(sseService, (envelope, clientPayload) -> { }, objectMapper);

    @Test
    void broadcast_WhenDelivered_PassesTheExcludedUserOn() throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(
                new SseEnvelope.Broadcast(new SseEnvelope.UserExclusion.User(7L), SseEventType.NOTIFY_PUBLISHED, Map.of("plannerId", "p-1")));

        subscriber.onMessage(
                new DefaultMessage(SseChannel.BROADCAST.topic().getBytes(StandardCharsets.UTF_8), body), null);

        assertThat(sseService.broadcasts).singleElement()
                .isEqualTo(new Broadcast(new SseEnvelope.UserExclusion.User(7L), SseEventType.NOTIFY_PUBLISHED.getValue()));
    }

    @Test
    void userEvent_WhenItExcludesADevice_SendsToTheUserExceptThatDevice() throws Exception {
        UUID device = UUID.randomUUID();
        byte[] body = objectMapper.writeValueAsBytes(new SseEnvelope.UserEvent(7L, SseEventType.NOTIFY_COMMENT, "n1",
                new SseEnvelope.DeviceExclusion.Device(device), Map.of("id", "n1")));

        subscriber.onMessage(
                new DefaultMessage(SseChannel.USER.topic().getBytes(StandardCharsets.UTF_8), body), null);

        assertThat(sseService.userSends).singleElement().isEqualTo(new UserSend(7L,
                new SseEnvelope.DeviceExclusion.Device(device), SseEventType.NOTIFY_COMMENT.getValue()));
    }

    @Test
    void broadcast_WhenNoUserIsExcluded_ReachesEverySubscriber() throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(new SseEnvelope.Broadcast(new SseEnvelope.UserExclusion.None(),
                SseEventType.NOTIFY_PUBLISHED, Map.of("plannerId", "p-1")));

        subscriber.onMessage(
                new DefaultMessage(SseChannel.BROADCAST.topic().getBytes(StandardCharsets.UTF_8), body), null);

        assertThat(sseService.broadcasts).singleElement()
                .isEqualTo(new Broadcast(new SseEnvelope.UserExclusion.None(), SseEventType.NOTIFY_PUBLISHED.getValue()));
    }

    private record UserSend(Long userId, SseEnvelope.DeviceExclusion exclusion, String eventType) {
    }

    private record Broadcast(SseEnvelope.UserExclusion exclusion, String eventType) {
    }

    private static final class RecordingSseService extends SseService {

        private final List<Broadcast> broadcasts = new ArrayList<>();
        private final List<UserSend> userSends = new ArrayList<>();

        RecordingSseService(ObjectMapper objectMapper) {
            super(objectMapper, userId -> new NotificationPreferenceProvider.Preferences(true, true, true),
                    new SyncTaskExecutor());
        }

        @Override
        public void broadcastToAll(SseEnvelope.UserExclusion exclusion, String eventType, Object data) {
            broadcasts.add(new Broadcast(exclusion, eventType));
        }

        @Override
        public void sendToUser(Long userId, SseEnvelope.DeviceExclusion exclusion, String eventType, Object data) {
            userSends.add(new UserSend(userId, exclusion, eventType));
        }
    }
}

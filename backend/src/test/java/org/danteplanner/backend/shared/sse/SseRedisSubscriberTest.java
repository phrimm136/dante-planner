package org.danteplanner.backend.shared.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                SseEnvelope.broadcast(7L, SseEventType.NOTIFY_PUBLISHED, Map.of("plannerId", "p-1")));

        subscriber.onMessage(
                new DefaultMessage(SseChannel.BROADCAST.topic().getBytes(StandardCharsets.UTF_8), body), null);

        assertThat(sseService.broadcasts).singleElement()
                .isEqualTo(new Broadcast(7L, SseEventType.NOTIFY_PUBLISHED.getValue()));
    }

    private record Broadcast(Long excludeUserId, String eventType) {
    }

    private static final class RecordingSseService extends SseService {

        private final List<Broadcast> broadcasts = new ArrayList<>();

        RecordingSseService(ObjectMapper objectMapper) {
            super(objectMapper, userId -> new NotificationPreferenceProvider.Preferences(true, true, true),
                    new SyncTaskExecutor());
        }

        @Override
        public void broadcastToAll(Long excludeUserId, String eventType, Object data) {
            broadcasts.add(new Broadcast(excludeUserId, eventType));
        }
    }
}

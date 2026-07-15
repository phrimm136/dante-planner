package org.danteplanner.backend.planner.service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.danteplanner.backend.comment.service.PlannerCommentSseService;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.shared.sse.SseRedisSubscriber;
import org.danteplanner.backend.shared.sse.SseService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PlannerSyncEventServiceTest {

    private static final String USER_CHANNEL = "sse:user";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private SseService sseService;

    @Mock
    private PlannerCommentSseService plannerCommentSseService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Drives the full cross-node path — notify, publish, deserialize, dispatch —
     * replacing only the Redis hop with a direct pipe of the published JSON into
     * the subscriber, and asserts the originating device is still excluded on the
     * receiving node.
     */
    @Test
    void notifyPlannerUpdate_AfterRedisFanout_ExcludesOriginatingDevice() {
        SsePublisher publisher = new SsePublisher(stringRedisTemplate, objectMapper);
        PlannerSyncEventService service = new PlannerSyncEventService(sseService, publisher);
        SseRedisSubscriber subscriber = new SseRedisSubscriber(sseService, plannerCommentSseService, objectMapper);
        UUID originatingDeviceId = UUID.randomUUID();
        UUID plannerId = UUID.randomUUID();

        service.notifyPlannerUpdate(1L, originatingDeviceId, plannerId, "updated", null);

        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(stringRedisTemplate).convertAndSend(eq(USER_CHANNEL), messageCaptor.capture());
        String publishedJson = (String) messageCaptor.getValue();
        subscriber.onMessage(new DefaultMessage(
                USER_CHANNEL.getBytes(StandardCharsets.UTF_8),
                publishedJson.getBytes(StandardCharsets.UTF_8)), null);

        verify(sseService).sendToUser(eq(1L), eq(originatingDeviceId), eq("updated"), any());
    }

    @Test
    void notifyPlannerUpdate_WhenPayloadGiven_PublishedEnvelopeCarriesSyncVersion() {
        SsePublisher publisher = new SsePublisher(stringRedisTemplate, objectMapper);
        PlannerSyncEventService service = new PlannerSyncEventService(sseService, publisher);
        UUID plannerId = UUID.randomUUID();

        service.notifyPlannerUpdate(1L, null, plannerId, "updated", Map.of("syncVersion", 7));

        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(stringRedisTemplate).convertAndSend(eq(USER_CHANNEL), messageCaptor.capture());
        assertThat((String) messageCaptor.getValue())
                .contains("syncVersion")
                .contains("7");
    }
}

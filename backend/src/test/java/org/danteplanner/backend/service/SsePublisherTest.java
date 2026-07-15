package org.danteplanner.backend.service;

import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SsePublisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SsePublisherTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishUserEvent_WhenCalled_PublishesPayloadEnvelopeToPrimaryUserChannel() {
        SsePublisher publisher = new SsePublisher(stringRedisTemplate, objectMapper);

        publisher.publishUserEvent(
                1L,
                null,
                SseEventType.UPDATED,
                "planner-9",
                Map.of("plannerId", "planner-9", "title", "Deck"));

        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(stringRedisTemplate).convertAndSend(eq("sse:user"), messageCaptor.capture());

        String message = (String) messageCaptor.getValue();
        assertThat(message)
                .contains("updated")
                .contains("planner-9")
                .contains("title")
                .contains("Deck");
    }
}

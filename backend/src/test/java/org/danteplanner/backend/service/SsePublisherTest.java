package org.danteplanner.backend.service;

import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SsePublisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SsePublisherTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    void publishUserEvent_WhenCalled_PublishesPayloadEnvelopeToPrimaryUserChannel() {
        SsePublisher publisher = new SsePublisher(stringRedisTemplate, objectMapper, meterRegistry);

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

    /**
     * Fan-out is best-effort, so an unreachable Redis stays swallowed — the counter is what keeps
     * the drop visible instead of silent.
     */
    @Test
    void publishUserEvent_WhenRedisUnreachable_CountsTheDropAndDoesNotThrow() {
        SsePublisher publisher = new SsePublisher(stringRedisTemplate, objectMapper, meterRegistry);
        when(stringRedisTemplate.convertAndSend(anyString(), any()))
                .thenThrow(new RedisConnectionFailureException("primary unreachable"));

        assertThatCode(() -> publisher.publishUserEvent(1L, SseEventType.UPDATED, "planner-9", Map.of()))
                .doesNotThrowAnyException();

        assertThat(meterRegistry.get("sse.publish.dropped").tag("channel", "USER").counter().count())
                .isEqualTo(1.0);
    }
}

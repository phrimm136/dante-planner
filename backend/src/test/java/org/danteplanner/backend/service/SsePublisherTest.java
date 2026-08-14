package org.danteplanner.backend.service;

import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SseChannelSender;
import org.danteplanner.backend.shared.sse.SsePublisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.retry.annotation.EnableRetry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SsePublisherTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    /**
     * The retry lives on a proxied collaborator, so exercising it needs a container to do the
     * proxying — an unproxied instance would run the annotation as documentation.
     */
    @Configuration
    @EnableRetry
    static class RetryHarness {

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return Mockito.mock(StringRedisTemplate.class);
        }

        @Bean
        SseChannelSender sseChannelSender(StringRedisTemplate stringRedisTemplate) {
            return new SseChannelSender(stringRedisTemplate);
        }
    }

    @Test
    void publishUserEvent_WhenCalled_PublishesPayloadEnvelopeToPrimaryUserChannel() {
        SsePublisher publisher = new SsePublisher(
                new SseChannelSender(stringRedisTemplate), objectMapper, meterRegistry);

        publisher.publishUserEvent(
                1L,
                null,
                SseEventType.COMMENT_ADDED,
                "planner-9",
                Map.of("plannerId", "planner-9", "title", "Deck"));

        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(stringRedisTemplate).convertAndSend(eq("sse:user"), messageCaptor.capture());

        String message = (String) messageCaptor.getValue();
        assertThat(message)
                .contains("comment:added")
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
        SsePublisher publisher = new SsePublisher(
                new SseChannelSender(stringRedisTemplate), objectMapper, meterRegistry);
        when(stringRedisTemplate.convertAndSend(anyString(), any()))
                .thenThrow(new RedisConnectionFailureException("primary unreachable"));

        assertThatCode(() -> publisher.publishUserEvent(1L, SseEventType.COMMENT_ADDED, "planner-9", Map.of()))
                .doesNotThrowAnyException();

        assertThat(meterRegistry.get("sse.publish.dropped").tag("channel", "USER").counter().count())
                .isEqualTo(1.0);
    }

    /**
     * A failover reassigns the primary in less time than these attempts span, so the drop counter
     * moving at all means the outage outlasted the retry rather than that a connection blinked.
     */
    @Test
    void publishUserEvent_WhenTheFirstTwoSendsFail_StillReachesRedisAndCountsNoDrop() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(RetryHarness.class)) {
            StringRedisTemplate template = context.getBean(StringRedisTemplate.class);
            when(template.convertAndSend(anyString(), any()))
                    .thenThrow(new RedisConnectionFailureException("primary unreachable"))
                    .thenThrow(new RedisConnectionFailureException("primary unreachable"))
                    .thenReturn(1L);

            SsePublisher publisher = new SsePublisher(
                    context.getBean(SseChannelSender.class), objectMapper, meterRegistry);

            publisher.publishUserEvent(1L, SseEventType.NOTIFY_COMMENT, "planner-9", Map.of());

            verify(template, times(3)).convertAndSend(eq("sse:user"), anyString());
            assertThat(meterRegistry.find("sse.publish.dropped").counter())
                    .as("the send succeeded, so nothing was dropped")
                    .isNull();
        }
    }

    /**
     * An envelope that will not serialize is a defect rather than an outage, and counting it as a
     * drop would hide it among the transient ones.
     */
    @Test
    void publishUserEvent_WhenTheEnvelopeWillNotSerialize_CountsItApartAndDoesNotThrow() {
        SsePublisher publisher = new SsePublisher(
                new SseChannelSender(stringRedisTemplate), objectMapper, meterRegistry);

        assertThatCode(() -> publisher.publishUserEvent(
                1L, SseEventType.NOTIFY_COMMENT, "planner-9", new Object()))
                .doesNotThrowAnyException();

        assertThat(meterRegistry.get("sse.publish.unserializable")
                .tag("channel", "USER").counter().count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.find("sse.publish.dropped").counter())
                .as("nothing reached Redis, so nothing was dropped there")
                .isNull();
    }
}

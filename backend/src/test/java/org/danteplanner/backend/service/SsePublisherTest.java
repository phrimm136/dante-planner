package org.danteplanner.backend.service;

import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SseChannelSender;
import org.danteplanner.backend.shared.sse.SseConstants;
import org.danteplanner.backend.shared.sse.SsePublisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.resilience.annotation.EnableResilientMethods;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @EnableResilientMethods
    static class RetryHarness {

        @Bean
        SseChannelSender sseChannelSender(StringRedisTemplate stringRedisTemplate) {
            return new SseChannelSender(stringRedisTemplate);
        }
    }

    private AnnotationConfigApplicationContext retryHarness() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(StringRedisTemplate.class, () -> stringRedisTemplate);
        context.register(RetryHarness.class);
        context.refresh();
        return context;
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
        try (AnnotationConfigApplicationContext context = retryHarness()) {
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
     * A Lettuce failover surfaces as a command timeout once the connection is already held, which
     * the connection-acquisition failure does not cover — so the retry has to name both or the
     * commoner symptom goes unretried.
     */
    @Test
    void publishUserEvent_WhenTheSendTimesOut_IsRetriedOnTheSameTerms() {
        try (AnnotationConfigApplicationContext context = retryHarness()) {
            StringRedisTemplate template = context.getBean(StringRedisTemplate.class);
            when(template.convertAndSend(anyString(), any()))
                    .thenThrow(new QueryTimeoutException("command timed out during failover"))
                    .thenThrow(new QueryTimeoutException("command timed out during failover"))
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
     * The drop counter is the signal that an outage outlasted the retry, so it must not move until
     * every attempt has been spent.
     */
    @Test
    void publishUserEvent_WhenEverySendFails_CountsOneDropAfterTheAttemptsAreSpent() {
        try (AnnotationConfigApplicationContext context = retryHarness()) {
            StringRedisTemplate template = context.getBean(StringRedisTemplate.class);
            when(template.convertAndSend(anyString(), any()))
                    .thenThrow(new RedisConnectionFailureException("primary unreachable"));

            SsePublisher publisher = new SsePublisher(
                    context.getBean(SseChannelSender.class), objectMapper, meterRegistry);

            assertThatCode(() -> publisher.publishUserEvent(
                    1L, SseEventType.NOTIFY_COMMENT, "planner-9", Map.of()))
                    .doesNotThrowAnyException();

            verify(template, times(SseConstants.PUBLISH_MAX_ATTEMPTS))
                    .convertAndSend(eq("sse:user"), anyString());
            assertThat(meterRegistry.get("sse.publish.dropped")
                    .tag("channel", "USER").counter().count())
                    .as("one publish dropped, not one per attempt")
                    .isEqualTo(1.0);
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

    @Test
    void send_WhenEveryCallFailsToConnect_BacksOffFiftyThenOneHundredMillis() {
        List<Long> invocationNanos = new ArrayList<>();
        RuntimeException failure = new RedisConnectionFailureException("primary unreachable");
        when(stringRedisTemplate.convertAndSend(anyString(), any())).thenAnswer(invocation -> {
            invocationNanos.add(System.nanoTime());
            throw failure;
        });

        try (AnnotationConfigApplicationContext context = retryHarness()) {
            SseChannelSender sender = context.getBean(SseChannelSender.class);

            assertThatThrownBy(() -> sender.send("sse:user", "{}"))
                    .isSameAs(failure);
        }

        assertThat(Duration.ofNanos(invocationNanos.get(1) - invocationNanos.get(0)))
                .isGreaterThanOrEqualTo(Duration.ofMillis(50));
        assertThat(Duration.ofNanos(invocationNanos.get(2) - invocationNanos.get(1)))
                .isGreaterThanOrEqualTo(Duration.ofMillis(100));
    }

    @Test
    void send_WhenAnUnlistedDataAccessExceptionIsThrown_IsNotRetried() {
        RuntimeException failure = new InvalidDataAccessApiUsageException("bad command");
        when(stringRedisTemplate.convertAndSend(anyString(), any())).thenThrow(failure);

        try (AnnotationConfigApplicationContext context = retryHarness()) {
            SseChannelSender sender = context.getBean(SseChannelSender.class);

            assertThatThrownBy(() -> sender.send("sse:user", "{}"))
                    .isSameAs(failure);
        }

        verify(stringRedisTemplate, times(1)).convertAndSend(eq("sse:user"), anyString());
    }

    @Test
    void send_WhenTheListedExceptionIsOnlyTheCause_IsRetriedOnTheSameTerms() {
        RuntimeException failure = new IllegalStateException(
                new RedisConnectionFailureException("primary unreachable"));
        when(stringRedisTemplate.convertAndSend(anyString(), any())).thenThrow(failure);

        try (AnnotationConfigApplicationContext context = retryHarness()) {
            SseChannelSender sender = context.getBean(SseChannelSender.class);

            assertThatThrownBy(() -> sender.send("sse:user", "{}"))
                    .isSameAs(failure);
        }

        verify(stringRedisTemplate, times(SseConstants.PUBLISH_MAX_ATTEMPTS))
                .convertAndSend(eq("sse:user"), anyString());
    }
}

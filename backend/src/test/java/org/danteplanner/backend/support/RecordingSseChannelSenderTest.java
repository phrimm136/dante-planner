package org.danteplanner.backend.support;

import java.util.Map;

import org.springframework.data.redis.RedisConnectionFailureException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SseChannel;
import org.danteplanner.backend.shared.sse.SseEnvelope;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.support.RecordingSseChannelSender.Failed;
import org.danteplanner.backend.support.RecordingSseChannelSender.Operation;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(ScriptedFailuresCheck.class)
class RecordingSseChannelSenderTest {

    private static final long USER_ID = 7L;

    private final RecordingSseChannelSender sender = new RecordingSseChannelSender();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final SsePublisher publisher = new SsePublisher(sender, new ObjectMapper(), meterRegistry);

    @Test
    void envelopes_WhenTheRealPublisherSendsAUserEvent_ShowItsTypeAndUser() {
        publisher.publishUserEvent(USER_ID, SseEventType.NOTIFY_COMMENT, "comment-1", Map.of("title", "Planner"));

        assertThat(sender.envelopes(SseChannel.USER)).singleElement().satisfies(envelope -> {
            assertThat(envelope.type()).isEqualTo(SseEventType.NOTIFY_COMMENT);
            assertThat(envelope.userId()).isEqualTo(USER_ID);
            assertThat(envelope.entityId()).isEqualTo("comment-1");
        });
        assertThat(sender.envelopes(SseChannel.COMMENT)).isEmpty();
    }

    @Test
    void failNext_WhenScripted_FailsOneSendAndThenRecovers() {
        RedisConnectionFailureException down = new RedisConnectionFailureException("down");
        sender.failNext(Operation.SEND, down);

        publisher.publishUserEvent(USER_ID, SseEventType.NOTIFY_COMMENT, "comment-1", Map.of("title", "Planner"));
        publisher.publishUserEvent(USER_ID, SseEventType.NOTIFY_COMMENT, "comment-2", Map.of("title", "Planner"));

        assertThat(meterRegistry.get("sse.publish.dropped").counter().count()).isEqualTo(1.0);
        assertThat(sender.envelopes(SseChannel.USER)).extracting(SseEnvelope::entityId).containsExactly("comment-2");
        assertThat(sender.attempts()).hasSize(2).first().isEqualTo(new Failed(Operation.SEND, down));
    }
}

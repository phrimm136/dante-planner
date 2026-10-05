package org.danteplanner.backend.support;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SseChannel;
import org.danteplanner.backend.shared.sse.SseEnvelope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseTopicRecorderTest {

    @Test
    void userEvents_WhenAnotherUsersEnvelopeShareTheTopic_ReturnsOnlyTheRequestedUsers() {
        SseTopicRecorder.Envelopes envelopes = new SseTopicRecorder.Envelopes();
        SseEnvelope own = SseEnvelope.userEvent(1L, SseEventType.NOTIFY_COMMENT, "c-1", null, Map.of());
        envelopes.record(SseChannel.USER, own);
        envelopes.record(SseChannel.USER, SseEnvelope.userEvent(2L, SseEventType.NOTIFY_COMMENT, "c-2", null, Map.of()));
        envelopes.record(SseChannel.USER, SseEnvelope.userEvent(1L, SseEventType.NOTIFY_PUBLISHED, "c-3", null, Map.of()));

        assertThat(envelopes.userEvents(1L, SseEventType.NOTIFY_COMMENT)).containsExactly(own);
    }

    @Test
    void commentEvents_WhenAnotherPlannersEnvelopeSharesTheTopic_ReturnsOnlyTheRequestedPlanners() {
        SseTopicRecorder.Envelopes envelopes = new SseTopicRecorder.Envelopes();
        UUID planner = UUID.randomUUID();
        SseEnvelope own = SseEnvelope.commentEvent(planner, SseEventType.COMMENT_ADDED, "c-1", 7L, Map.of());
        envelopes.record(SseChannel.COMMENT, own);
        envelopes.record(SseChannel.COMMENT,
                SseEnvelope.commentEvent(UUID.randomUUID(), SseEventType.COMMENT_ADDED, "c-2", 7L, Map.of()));

        assertThat(envelopes.commentEvents(planner, SseEventType.COMMENT_ADDED)).containsExactly(own);
    }

    @Test
    void broadcasts_WhenThePayloadPredicateRejects_ExcludesTheEnvelope() {
        SseTopicRecorder.Envelopes envelopes = new SseTopicRecorder.Envelopes();
        SseEnvelope own = SseEnvelope.broadcast(1L, SseEventType.NOTIFY_PUBLISHED, Map.of("plannerId", "p-1"));
        envelopes.record(SseChannel.BROADCAST, own);
        envelopes.record(SseChannel.BROADCAST,
                SseEnvelope.broadcast(1L, SseEventType.NOTIFY_PUBLISHED, Map.of("plannerId", "p-2")));

        assertThat(envelopes.broadcasts(SseEventType.NOTIFY_PUBLISHED,
                payload -> payload instanceof Map<?, ?> map && "p-1".equals(map.get("plannerId"))))
                .containsExactly(own);
    }

    @Test
    void await_WhenAnEnvelopeArrivesFromAnotherThread_ReturnsIt() throws Exception {
        SseTopicRecorder.Envelopes envelopes = new SseTopicRecorder.Envelopes();
        SseEnvelope arriving = SseEnvelope.userEvent(3L, SseEventType.NOTIFY_COMMENT, "c-1", null, Map.of());
        Thread publisher = new Thread(() -> envelopes.record(SseChannel.USER, arriving));
        publisher.start();

        assertThat(envelopes.await(() -> envelopes.userEvents(3L, SseEventType.NOTIFY_COMMENT),
                Duration.ofSeconds(5), "the arriving envelope")).containsExactly(arriving);
        publisher.join();
    }

    @Test
    void await_WhenNothingArrives_FailsNamingWhatItWaitedFor() {
        SseTopicRecorder.Envelopes envelopes = new SseTopicRecorder.Envelopes();

        assertThatThrownBy(() -> envelopes.await(() -> envelopes.userEvents(4L, SseEventType.NOTIFY_COMMENT),
                Duration.ofMillis(50), "a NOTIFY_COMMENT envelope for user 4"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("waited PT0.05S for a NOTIFY_COMMENT envelope for user 4; none was published");
    }
}

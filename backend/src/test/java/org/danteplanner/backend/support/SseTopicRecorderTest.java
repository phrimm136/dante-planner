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
        SseEnvelope.Wire own = new SseEnvelope.UserEvent(1L, SseEventType.NOTIFY_COMMENT, "c-1", Map.of()).wire();
        envelopes.record(SseChannel.USER, own);
        envelopes.record(SseChannel.USER, new SseEnvelope.UserEvent(2L, SseEventType.NOTIFY_COMMENT, "c-2", Map.of()).wire());
        envelopes.record(SseChannel.USER, new SseEnvelope.UserEvent(1L, SseEventType.NOTIFY_PUBLISHED, "c-3", Map.of()).wire());

        assertThat(envelopes.userEvents(1L, SseEventType.NOTIFY_COMMENT)).containsExactly(own);
    }

    @Test
    void commentEvents_WhenAnotherPlannersEnvelopeSharesTheTopic_ReturnsOnlyTheRequestedPlanners() {
        SseTopicRecorder.Envelopes envelopes = new SseTopicRecorder.Envelopes();
        UUID planner = UUID.randomUUID();
        SseEnvelope.Wire own = new SseEnvelope.CommentEvent(planner, SseEventType.COMMENT_ADDED, "c-1", new SseEnvelope.UserExclusion.User(7L), Map.of()).wire();
        envelopes.record(SseChannel.COMMENT, own);
        envelopes.record(SseChannel.COMMENT,
                new SseEnvelope.CommentEvent(UUID.randomUUID(), SseEventType.COMMENT_ADDED, "c-2", new SseEnvelope.UserExclusion.User(7L), Map.of()).wire());

        assertThat(envelopes.commentEvents(planner, SseEventType.COMMENT_ADDED)).containsExactly(own);
    }

    @Test
    void broadcasts_WhenThePayloadPredicateRejects_ExcludesTheEnvelope() {
        SseTopicRecorder.Envelopes envelopes = new SseTopicRecorder.Envelopes();
        SseEnvelope.Wire own = new SseEnvelope.Broadcast(new SseEnvelope.UserExclusion.User(1L), SseEventType.NOTIFY_PUBLISHED, Map.of("plannerId", "p-1")).wire();
        envelopes.record(SseChannel.BROADCAST, own);
        envelopes.record(SseChannel.BROADCAST,
                new SseEnvelope.Broadcast(new SseEnvelope.UserExclusion.User(1L), SseEventType.NOTIFY_PUBLISHED, Map.of("plannerId", "p-2")).wire());

        assertThat(envelopes.broadcasts(SseEventType.NOTIFY_PUBLISHED,
                payload -> payload instanceof Map<?, ?> map && "p-1".equals(map.get("plannerId"))))
                .containsExactly(own);
    }

    @Test
    void await_WhenAnEnvelopeArrivesFromAnotherThread_ReturnsIt() {
        SseTopicRecorder.Envelopes envelopes = new SseTopicRecorder.Envelopes();
        SseEnvelope.Wire arriving = new SseEnvelope.UserEvent(3L, SseEventType.NOTIFY_COMMENT, "c-1", Map.of()).wire();
        Thread publisher = new Thread(() -> envelopes.record(SseChannel.USER, arriving));
        publisher.start();

        assertThat(envelopes.await(() -> envelopes.userEvents(3L, SseEventType.NOTIFY_COMMENT),
                Duration.ofSeconds(5), "the arriving envelope")).containsExactly(arriving);
        Hang.ended(publisher, "the publisher ending");
    }

    @Test
    void await_WhenNothingArrives_FailsNamingWhatItWaitedFor() {
        SseTopicRecorder.Envelopes envelopes = new SseTopicRecorder.Envelopes();

        assertThatThrownBy(() -> envelopes.await(() -> envelopes.userEvents(4L, SseEventType.NOTIFY_COMMENT),
                Duration.ofMillis(50), "a NOTIFY_COMMENT envelope for user 4"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("hang: a NOTIFY_COMMENT envelope for user 4 did not complete within 50ms");
    }
}

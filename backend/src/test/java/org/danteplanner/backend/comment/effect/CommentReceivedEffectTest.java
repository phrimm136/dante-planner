package org.danteplanner.backend.comment.effect;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.planner.api.PlannerNotificationTarget;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEventPayloadReader;
import org.danteplanner.backend.shared.outbox.service.EffectPushQueue;
import org.danteplanner.backend.shared.sse.SseChannel;
import org.danteplanner.backend.shared.sse.SseEnvelope;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.support.FakePublishedPlanners;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.RecordingNotifications;
import org.danteplanner.backend.support.RecordingNotifications.CommentReceived;
import org.danteplanner.backend.support.RecordingSseChannelSender;
import org.danteplanner.backend.support.UsersContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

/**
 * Who hears about a top-level comment, decided where the rows that answer it are held.
 *
 * <p>The thread push is separated from the notification on purpose: a self-comment and a comment
 * whose recipient switched notifications off both still update every open thread, because that push
 * is how a reader's view stays current rather than a message addressed to anyone.</p>
 */
@ExtendWith(MockitoExtension.class)
class CommentReceivedEffectTest {

    private static final UUID PLANNER_ID = UUID.randomUUID();
    private static final Long OWNER_ID = 1L;
    private static final Long COMMENTER_ID = 2L;
    private static final Long COMMENT_ID = 77L;

    @Mock
    private PlannerCommentRepository commentRepository;

    private final FakeUsers users = new FakeUsers();

    private final FakePublishedPlanners publishedPlanners = new FakePublishedPlanners();

    private final RecordingNotifications notifications = new RecordingNotifications(users);

    private final RecordingSseChannelSender sseChannel = new RecordingSseChannelSender();

    private final SsePublisher ssePublisher =
            new SsePublisher(sseChannel, Jackson2ObjectMapperBuilder.json().build(), new SimpleMeterRegistry());

    private CommentReceivedEffect effect;

    @BeforeEach
    void setUp() {
        effect = new CommentReceivedEffect(commentRepository,
                new CommentQueryService(commentRepository, null, users, null, null),
                publishedPlanners, notifications,
                new DomainEventPayloadReader(new ObjectMapper()));

        users.add(UsersContract.newUser().id(OWNER_ID).build());
    }

    @Test
    void type_WhenAsked_IsCommentReceived() {
        assertThat(effect.type()).isEqualTo(DomainEventType.COMMENT_RECEIVED);
    }

    @Test
    void applyEffect_WhenTheOwnerIsEligible_NotifiesAndPushesBoth() {
        PlannerComment comment = givenAComment(COMMENTER_ID);
        givenAPlanner(true);

        applyAndFlush();

        assertThat(notifications.calls()).containsExactly(new CommentReceived(
                COMMENT_ID, comment.getPublicId(), PLANNER_ID, "Test Planner", "A comment", OWNER_ID));
        assertThat(sseChannel.envelopes(SseChannel.USER))
                .extracting(SseEnvelope::type, SseEnvelope::userId)
                .containsExactly(tuple(SseEventType.NOTIFY_COMMENT, OWNER_ID));
        assertThat(sseChannel.envelopes(SseChannel.COMMENT))
                .extracting(SseEnvelope::type, SseEnvelope::plannerId, SseEnvelope::excludeUserId)
                .containsExactly(tuple(SseEventType.COMMENT_ADDED, PLANNER_ID.toString(), COMMENTER_ID));
    }

    @Test
    void applyEffect_WhenTheCommenterOwnsThePlanner_NotifiesNobodyButStillPushesTheThread() {
        givenAComment(OWNER_ID);
        givenAPlanner(true);

        applyAndFlush();

        assertThat(notifications.calls()).isEmpty();
        assertThat(sseChannel.envelopes(SseChannel.USER)).isEmpty();
        assertThat(sseChannel.envelopes(SseChannel.COMMENT))
                .extracting(SseEnvelope::type, SseEnvelope::plannerId, SseEnvelope::excludeUserId)
                .containsExactly(tuple(SseEventType.COMMENT_ADDED, PLANNER_ID.toString(), OWNER_ID));
    }

    @Test
    void applyEffect_WhenTheOwnerDisabledNotifications_NotifiesNobodyButStillPushesTheThread() {
        givenAComment(COMMENTER_ID);
        givenAPlanner(false);

        applyAndFlush();

        assertThat(notifications.calls()).isEmpty();
        assertThat(sseChannel.envelopes(SseChannel.USER)).isEmpty();
        assertThat(sseChannel.envelopes(SseChannel.COMMENT))
                .extracting(SseEnvelope::type, SseEnvelope::plannerId, SseEnvelope::excludeUserId)
                .containsExactly(tuple(SseEventType.COMMENT_ADDED, PLANNER_ID.toString(), COMMENTER_ID));
    }

    @Test
    void applyEffect_WhenTheCommentIsGone_AnnouncesNothing() {
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

        applyAndFlush();

        assertThat(notifications.calls()).isEmpty();
        assertThat(sseChannel.attempts()).isEmpty();
    }

    @Test
    void applyEffect_WhenTheCommentWasWithdrawn_AnnouncesNothing() {
        PlannerComment comment = new PlannerComment(PLANNER_ID, COMMENTER_ID, "A comment", null, 0);
        comment.setId(COMMENT_ID);
        comment.setPublicId(UUID.randomUUID());
        comment.setCreatedAt(Instant.now());
        comment.softDelete();
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

        applyAndFlush();

        assertThat(notifications.calls()).isEmpty();
        assertThat(sseChannel.attempts()).isEmpty();
    }

    @Test
    void applyEffect_WhenThePlannerIsGone_AnnouncesNothing() {
        givenAComment(COMMENTER_ID);

        applyAndFlush();

        assertThat(notifications.calls()).isEmpty();
        assertThat(sseChannel.attempts()).isEmpty();
    }

    /**
     * Runs the arm and releases its queue, which is what the dispatch commit does in production.
     */
    private void applyAndFlush() {
        EffectPushQueue pushes = new EffectPushQueue(ssePublisher);
        effect.applyEffect(event(), pushes);
        pushes.flush();
    }

    private PlannerComment givenAComment(Long authorId) {
        PlannerComment comment = new PlannerComment(PLANNER_ID, authorId, "A comment", null, 0);
        comment.setId(COMMENT_ID);
        comment.setPublicId(UUID.randomUUID());
        comment.setCreatedAt(Instant.now());
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        return comment;
    }

    private void givenAPlanner(boolean ownerNotificationsEnabled) {
        publishedPlanners.add(new PlannerNotificationTarget(PLANNER_ID, "Test Planner", OWNER_ID,
                ownerNotificationsEnabled), true);
    }

    private static DomainEvent event() {
        return DomainEvent.of(DomainEventType.COMMENT_RECEIVED, PLANNER_ID,
                "{\"commentId\":" + COMMENT_ID + "}");
    }
}

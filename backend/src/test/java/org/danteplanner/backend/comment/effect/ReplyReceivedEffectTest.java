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
import org.danteplanner.backend.support.RecordingNotifications.ReplyReceived;
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
 * Who hears about a reply, on the same terms as {@link CommentReceivedEffectTest}: the recipient is
 * the parent's author, and the thread push happens either way.
 */
@ExtendWith(MockitoExtension.class)
class ReplyReceivedEffectTest {

    private static final UUID PLANNER_ID = UUID.randomUUID();
    private static final UUID PARENT_PUBLIC_ID = UUID.randomUUID();
    private static final Long OWNER_ID = 1L;
    private static final Long REPLIER_ID = 2L;
    private static final Long PARENT_AUTHOR_ID = 3L;
    private static final Long PARENT_ID = 50L;
    private static final Long REPLY_ID = 101L;

    @Mock
    private PlannerCommentRepository commentRepository;

    private final FakeUsers users = new FakeUsers();

    private final FakePublishedPlanners publishedPlanners = new FakePublishedPlanners();

    private final RecordingNotifications notifications = new RecordingNotifications(users);

    private final RecordingSseChannelSender sseChannel = new RecordingSseChannelSender();

    private final SsePublisher ssePublisher =
            new SsePublisher(sseChannel, Jackson2ObjectMapperBuilder.json().build(), new SimpleMeterRegistry());

    private ReplyReceivedEffect effect;

    @BeforeEach
    void setUp() {
        effect = new ReplyReceivedEffect(commentRepository,
                new CommentQueryService(commentRepository, null, users, null, null),
                publishedPlanners, notifications,
                new DomainEventPayloadReader(new ObjectMapper()));
        publishedPlanners.add(new PlannerNotificationTarget(PLANNER_ID, "Test Planner", OWNER_ID, true), true);
        users.add(UsersContract.newUser().id(OWNER_ID).build());
        users.add(UsersContract.newUser().id(PARENT_AUTHOR_ID).build());
    }

    @Test
    void type_WhenAsked_IsReplyReceived() {
        assertThat(effect.type()).isEqualTo(DomainEventType.REPLY_RECEIVED);
    }

    @Test
    void applyEffect_WhenTheParentAuthorIsEligible_NotifiesAndPushesBoth() {
        PlannerComment reply = givenAReplyTo(givenAParent(PARENT_AUTHOR_ID, true));

        applyAndFlush();

        assertThat(notifications.calls()).containsExactly(new ReplyReceived(
                REPLY_ID, reply.getPublicId(), PLANNER_ID, "Test Planner", "A reply", PARENT_AUTHOR_ID));
        assertThat(sseChannel.envelopes(SseChannel.USER))
                .extracting(SseEnvelope::type, SseEnvelope::userId)
                .containsExactly(tuple(SseEventType.NOTIFY_COMMENT, PARENT_AUTHOR_ID));
        assertThat(sseChannel.envelopes(SseChannel.COMMENT))
                .extracting(SseEnvelope::type, SseEnvelope::plannerId, SseEnvelope::excludeUserId)
                .containsExactly(tuple(SseEventType.COMMENT_ADDED, PLANNER_ID.toString(), REPLIER_ID));
    }

    @Test
    void applyEffect_WhenTheReplierWroteTheParent_NotifiesNobodyButStillPushesTheThread() {
        givenAReplyTo(givenAParent(REPLIER_ID, true));

        applyAndFlush();

        assertThat(notifications.calls()).isEmpty();
        assertThat(sseChannel.envelopes(SseChannel.USER)).isEmpty();
        assertThat(sseChannel.envelopes(SseChannel.COMMENT))
                .extracting(SseEnvelope::type, SseEnvelope::plannerId, SseEnvelope::excludeUserId)
                .containsExactly(tuple(SseEventType.COMMENT_ADDED, PLANNER_ID.toString(), REPLIER_ID));
    }

    @Test
    void applyEffect_WhenTheParentAuthorDisabledNotifications_NotifiesNobodyButStillPushesTheThread() {
        givenAReplyTo(givenAParent(PARENT_AUTHOR_ID, false));

        applyAndFlush();

        assertThat(notifications.calls()).isEmpty();
        assertThat(sseChannel.envelopes(SseChannel.USER)).isEmpty();
        assertThat(sseChannel.envelopes(SseChannel.COMMENT))
                .extracting(SseEnvelope::type, SseEnvelope::plannerId, SseEnvelope::excludeUserId)
                .containsExactly(tuple(SseEventType.COMMENT_ADDED, PLANNER_ID.toString(), REPLIER_ID));
    }

    @Test
    void applyEffect_WhenTheReplyWasWithdrawn_AnnouncesNothing() {
        PlannerComment parent = parent(PARENT_AUTHOR_ID, true);
        PlannerComment reply =
                new PlannerComment(PLANNER_ID, REPLIER_ID, "A reply", parent.getId(), 1);
        reply.setId(REPLY_ID);
        reply.setPublicId(UUID.randomUUID());
        reply.setCreatedAt(Instant.now());
        reply.softDelete();
        when(commentRepository.findById(REPLY_ID)).thenReturn(Optional.of(reply));

        applyAndFlush();

        assertThat(notifications.calls()).isEmpty();
        assertThat(sseChannel.attempts()).isEmpty();
    }

    @Test
    void applyEffect_WhenTheReplyIsGone_AnnouncesNothing() {
        when(commentRepository.findById(REPLY_ID)).thenReturn(Optional.empty());

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

    private static PlannerComment parent(Long authorId, boolean authorNotificationsEnabled) {
        PlannerComment parent = new PlannerComment(PLANNER_ID, authorId, "Parent", null, 0);
        parent.setId(PARENT_ID);
        parent.setPublicId(PARENT_PUBLIC_ID);
        parent.setCreatedAt(Instant.now());
        parent.setAuthorNotificationsEnabled(authorNotificationsEnabled);
        return parent;
    }

    private PlannerComment givenAParent(Long authorId, boolean authorNotificationsEnabled) {
        PlannerComment parent = parent(authorId, authorNotificationsEnabled);
        when(commentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent));
        return parent;
    }

    private PlannerComment givenAReplyTo(PlannerComment parent) {
        PlannerComment reply =
                new PlannerComment(PLANNER_ID, REPLIER_ID, "A reply", parent.getId(), 1);
        reply.setId(REPLY_ID);
        reply.setPublicId(UUID.randomUUID());
        reply.setCreatedAt(Instant.now());
        when(commentRepository.findById(REPLY_ID)).thenReturn(Optional.of(reply));
        return reply;
    }

    private static DomainEvent event() {
        return DomainEvent.of(DomainEventType.REPLY_RECEIVED, PLANNER_ID,
                "{\"replyId\":" + REPLY_ID + "}");
    }
}

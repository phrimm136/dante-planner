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
 * The comment and reply arms hold their public pushes to the raise site's predicate: a planner that
 * left public view gets no COMMENT_ADDED on its public channel, and a withdrawn parent gets no reply
 * notification.
 *
 * <p>The notification target is present for an unpublished planner because its query filters only
 * {@code c.deletedAt}; the planner's published state is what {@code isPublished} answers.</p>
 */
@ExtendWith(MockitoExtension.class)
class CommentEffectPublicVisibilityTest {

    private static final UUID PLANNER_ID = UUID.randomUUID();
    private static final Long OWNER_ID = 1L;
    private static final Long WRITER_ID = 2L;
    private static final Long PARENT_AUTHOR_ID = 3L;
    private static final Long COMMENT_ID = 77L;
    private static final Long PARENT_ID = 50L;
    private static final Long REPLY_ID = 101L;
    private static final PlannerNotificationTarget TARGET =
            new PlannerNotificationTarget(PLANNER_ID, "Test Planner", OWNER_ID, true);

    @Mock
    private PlannerCommentRepository commentRepository;

    private final FakeUsers users = new FakeUsers();

    private final FakePublishedPlanners publishedPlanners = new FakePublishedPlanners();

    private final RecordingNotifications notifications = new RecordingNotifications(users);

    private final RecordingSseChannelSender sseChannel = new RecordingSseChannelSender();

    private final SsePublisher ssePublisher =
            new SsePublisher(sseChannel, Jackson2ObjectMapperBuilder.json().build(), new SimpleMeterRegistry());

    private final DomainEventPayloadReader payloads = new DomainEventPayloadReader(new ObjectMapper());

    @BeforeEach
    void setUp() {
        publishedPlanners.add(TARGET, true);
        users.add(UsersContract.newUser().id(OWNER_ID).build());
        users.add(UsersContract.newUser().id(PARENT_AUTHOR_ID).build());
    }

    private CommentQueryService commentQueryService() {
        return new CommentQueryService(commentRepository, null, users, null, null);
    }

    @Test
    void commentReceived_WhenPlannerUnpublishedBeforeDispatch_PushesNoPublicCommentAdded() {
        givenUnpublished();
        PlannerComment comment = comment(COMMENT_ID, WRITER_ID, null);
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        CommentReceivedEffect effect = new CommentReceivedEffect(commentRepository, commentQueryService(),
                publishedPlanners, notifications, payloads);

        EffectPushQueue pushes = new EffectPushQueue(ssePublisher);
        effect.applyEffect(DomainEvent.of(DomainEventType.COMMENT_RECEIVED, PLANNER_ID,
                "{\"commentId\":" + COMMENT_ID + "}"), pushes);
        pushes.flush();

        assertThat(sseChannel.envelopes(SseChannel.COMMENT)).isEmpty();
    }

    @Test
    void replyReceived_WhenPlannerUnpublishedBeforeDispatch_PushesNoPublicCommentAdded() {
        givenUnpublished();
        when(commentRepository.findById(PARENT_ID))
                .thenReturn(Optional.of(comment(PARENT_ID, PARENT_AUTHOR_ID, null)));
        when(commentRepository.findById(REPLY_ID))
                .thenReturn(Optional.of(comment(REPLY_ID, WRITER_ID, PARENT_ID)));

        applyReply();

        assertThat(sseChannel.envelopes(SseChannel.COMMENT)).isEmpty();
    }

    @Test
    void replyReceived_WhenParentWithdrawnBeforeDispatch_NotifiesNobody() {
        PlannerComment parent = comment(PARENT_ID, PARENT_AUTHOR_ID, null);
        parent.softDelete();
        when(commentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent));
        when(commentRepository.findById(REPLY_ID))
                .thenReturn(Optional.of(comment(REPLY_ID, WRITER_ID, PARENT_ID)));

        applyReply();

        assertThat(notifications.calls()).isEmpty();
        assertThat(sseChannel.envelopes(SseChannel.USER)).isEmpty();
    }

    private void givenUnpublished() {
        publishedPlanners.add(TARGET, false);
    }

    @Test
    void commentReceived_WhenPlannerUnpublishedBeforeDispatch_StillNotifiesTheOwner() {
        givenUnpublished();
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment(COMMENT_ID, WRITER_ID, null)));
        CommentReceivedEffect effect = new CommentReceivedEffect(commentRepository, commentQueryService(),
                publishedPlanners, notifications, payloads);

        EffectPushQueue pushes = new EffectPushQueue(ssePublisher);
        effect.applyEffect(DomainEvent.of(DomainEventType.COMMENT_RECEIVED, PLANNER_ID,
                "{\"commentId\":" + COMMENT_ID + "}"), pushes);
        pushes.flush();

        assertThat(notifications.calls()).singleElement().isInstanceOfSatisfying(CommentReceived.class, call -> {
            assertThat(call.commentId()).isEqualTo(COMMENT_ID);
            assertThat(call.plannerId()).isEqualTo(PLANNER_ID);
            assertThat(call.plannerOwnerId()).isEqualTo(OWNER_ID);
        });
        assertNotifyCommentPushedTo(OWNER_ID);
    }

    @Test
    void replyReceived_WhenParentWithdrawnBeforeDispatch_StillPushesTheReplyToTheThread() {
        PlannerComment parent = comment(PARENT_ID, PARENT_AUTHOR_ID, null);
        parent.softDelete();
        when(commentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent));
        when(commentRepository.findById(REPLY_ID))
                .thenReturn(Optional.of(comment(REPLY_ID, WRITER_ID, PARENT_ID)));

        applyReply();

        assertThat(sseChannel.envelopes(SseChannel.COMMENT))
                .extracting(SseEnvelope::type, SseEnvelope::plannerId, SseEnvelope::excludeUserId)
                .containsExactly(tuple(SseEventType.COMMENT_ADDED, PLANNER_ID.toString(), WRITER_ID));
    }

    @Test
    void replyReceived_WhenPlannerUnpublishedAndParentAuthorIsNotTheOwner_NotifiesNobody() {
        givenUnpublished();
        givenAReplyUnder(PARENT_AUTHOR_ID);

        applyReply();

        assertThat(notifications.calls()).isEmpty();
        assertThat(sseChannel.envelopes(SseChannel.USER)).isEmpty();
    }

    @Test
    void replyReceived_WhenPlannerUnpublishedAndParentAuthorIsTheOwner_NotifiesTheOwner() {
        givenUnpublished();
        givenAReplyUnder(OWNER_ID);

        applyReply();

        assertReplyNotifiedTo(OWNER_ID);
        assertNotifyCommentPushedTo(OWNER_ID);
    }

    @Test
    void replyReceived_WhenPlannerPublishedAndParentAuthorIsNotTheOwner_NotifiesTheParentAuthor() {
        givenAReplyUnder(PARENT_AUTHOR_ID);

        applyReply();

        assertReplyNotifiedTo(PARENT_AUTHOR_ID);
        assertNotifyCommentPushedTo(PARENT_AUTHOR_ID);
    }

    private void assertReplyNotifiedTo(Long recipientId) {
        assertThat(notifications.calls()).singleElement().isInstanceOfSatisfying(ReplyReceived.class, call -> {
            assertThat(call.replyId()).isEqualTo(REPLY_ID);
            assertThat(call.plannerId()).isEqualTo(PLANNER_ID);
            assertThat(call.parentAuthorId()).isEqualTo(recipientId);
        });
    }

    private void assertNotifyCommentPushedTo(Long recipientId) {
        assertThat(sseChannel.envelopes(SseChannel.USER))
                .extracting(SseEnvelope::type, SseEnvelope::userId)
                .containsExactly(tuple(SseEventType.NOTIFY_COMMENT, recipientId));
    }

    private void givenAReplyUnder(Long parentAuthorId) {
        when(commentRepository.findById(PARENT_ID)).thenReturn(Optional.of(comment(PARENT_ID, parentAuthorId, null)));
        when(commentRepository.findById(REPLY_ID))
                .thenReturn(Optional.of(comment(REPLY_ID, WRITER_ID, PARENT_ID)));
    }

    private void applyReply() {
        ReplyReceivedEffect effect = new ReplyReceivedEffect(commentRepository, commentQueryService(),
                publishedPlanners, notifications, payloads);
        EffectPushQueue pushes = new EffectPushQueue(ssePublisher);
        effect.applyEffect(DomainEvent.of(DomainEventType.REPLY_RECEIVED, PLANNER_ID,
                "{\"replyId\":" + REPLY_ID + "}"), pushes);
        pushes.flush();
    }

    private static PlannerComment comment(Long id, Long authorId, Long parentId) {
        PlannerComment comment = new PlannerComment(PLANNER_ID, authorId, "text " + id, parentId,
                parentId == null ? 0 : 1);
        comment.setId(id);
        comment.setPublicId(UUID.randomUUID());
        comment.setCreatedAt(Instant.now());
        comment.setAuthorNotificationsEnabled(true);
        return comment;
    }
}

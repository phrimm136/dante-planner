package org.danteplanner.backend.comment.effect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.notification.dto.NotificationEventPayload;
import org.danteplanner.backend.notification.service.NotificationDispatchService;
import org.danteplanner.backend.notification.service.NotificationOutcome;
import org.danteplanner.backend.planner.dto.PlannerNotificationTarget;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEventPayloadReader;
import org.danteplanner.backend.shared.outbox.service.EffectPushQueue;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
@MockitoSettings(strictness = Strictness.LENIENT)
class CommentEffectPublicVisibilityTest {

    private static final UUID PLANNER_ID = UUID.randomUUID();
    private static final Long OWNER_ID = 1L;
    private static final Long WRITER_ID = 2L;
    private static final Long PARENT_AUTHOR_ID = 3L;
    private static final Long COMMENT_ID = 77L;
    private static final Long PARENT_ID = 50L;
    private static final Long REPLY_ID = 101L;

    @Mock
    private PlannerCommentRepository commentRepository;

    @Mock
    private CommentQueryService commentQueryService;

    @Mock
    private PublishedPlannerQueryService plannerQueryService;

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @Mock
    private SsePublisher ssePublisher;

    private final DomainEventPayloadReader payloads = new DomainEventPayloadReader(new ObjectMapper());

    @BeforeEach
    void setUp() {
        when(plannerQueryService.notificationTargetOf(PLANNER_ID)).thenReturn(Optional.of(
                new PlannerNotificationTarget(PLANNER_ID, "Test Planner", OWNER_ID, true)));
        when(plannerQueryService.isPublished(PLANNER_ID)).thenReturn(true);
    }

    @Test
    @DisplayName("a comment on a planner unpublished before dispatch pushes no COMMENT_ADDED to the public channel")
    void commentReceived_WhenPlannerUnpublishedBeforeDispatch_PushesNoPublicCommentAdded() {
        givenUnpublished();
        PlannerComment comment = comment(COMMENT_ID, WRITER_ID, null);
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        CommentReceivedEffect effect = new CommentReceivedEffect(commentRepository, commentQueryService,
                plannerQueryService, notificationDispatchService, payloads);

        EffectPushQueue pushes = new EffectPushQueue(ssePublisher);
        effect.applyEffect(DomainEvent.of(DomainEventType.COMMENT_RECEIVED, PLANNER_ID,
                "{\"commentId\":" + COMMENT_ID + "}"), pushes);
        pushes.flush();

        verify(ssePublisher, never()).publishCommentEvent(
                eq(PLANNER_ID), eq(SseEventType.COMMENT_ADDED), any(), any(), any());
    }

    @Test
    @DisplayName("a reply on a planner unpublished before dispatch pushes no COMMENT_ADDED to the public channel")
    void replyReceived_WhenPlannerUnpublishedBeforeDispatch_PushesNoPublicCommentAdded() {
        givenUnpublished();
        when(commentRepository.findById(PARENT_ID))
                .thenReturn(Optional.of(comment(PARENT_ID, PARENT_AUTHOR_ID, null)));
        when(commentRepository.findById(REPLY_ID))
                .thenReturn(Optional.of(comment(REPLY_ID, WRITER_ID, PARENT_ID)));

        applyReply();

        verify(ssePublisher, never()).publishCommentEvent(
                eq(PLANNER_ID), eq(SseEventType.COMMENT_ADDED), any(), any(), any());
    }

    @Test
    @DisplayName("a reply under a parent withdrawn before dispatch notifies nobody")
    void replyReceived_WhenParentWithdrawnBeforeDispatch_NotifiesNobody() {
        PlannerComment parent = comment(PARENT_ID, PARENT_AUTHOR_ID, null);
        parent.softDelete();
        when(commentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent));
        when(commentRepository.findById(REPLY_ID))
                .thenReturn(Optional.of(comment(REPLY_ID, WRITER_ID, PARENT_ID)));

        applyReply();

        verify(notificationDispatchService, never()).notifyReplyReceived(
                anyLong(), any(), any(), any(), any(), anyLong());
        verify(ssePublisher, never()).publishUserEvent(
                eq(PARENT_AUTHOR_ID), eq(SseEventType.NOTIFY_COMMENT), any(), any());
    }

    private void givenUnpublished() {
        when(plannerQueryService.isPublished(PLANNER_ID)).thenReturn(false);
    }

    @Test
    @DisplayName("the owner of a planner unpublished before dispatch still gets the comment notification and its push")
    void commentReceived_WhenPlannerUnpublishedBeforeDispatch_StillNotifiesTheOwner() {
        givenUnpublished();
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment(COMMENT_ID, WRITER_ID, null)));
        when(notificationDispatchService.notifyCommentReceived(anyLong(), any(), any(), any(), any(), anyLong()))
                .thenReturn(delivered(OWNER_ID));
        CommentReceivedEffect effect = new CommentReceivedEffect(commentRepository, commentQueryService,
                plannerQueryService, notificationDispatchService, payloads);

        EffectPushQueue pushes = new EffectPushQueue(ssePublisher);
        effect.applyEffect(DomainEvent.of(DomainEventType.COMMENT_RECEIVED, PLANNER_ID,
                "{\"commentId\":" + COMMENT_ID + "}"), pushes);
        pushes.flush();

        verify(notificationDispatchService).notifyCommentReceived(
                eq(COMMENT_ID), any(), eq(PLANNER_ID), any(), any(), eq(OWNER_ID));
        verify(ssePublisher).publishUserEvent(eq(OWNER_ID), eq(SseEventType.NOTIFY_COMMENT), any(), any());
    }

    @Test
    @DisplayName("a reply under a parent withdrawn before dispatch still pushes the live reply to the thread")
    void replyReceived_WhenParentWithdrawnBeforeDispatch_StillPushesTheReplyToTheThread() {
        PlannerComment parent = comment(PARENT_ID, PARENT_AUTHOR_ID, null);
        parent.softDelete();
        when(commentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent));
        when(commentRepository.findById(REPLY_ID))
                .thenReturn(Optional.of(comment(REPLY_ID, WRITER_ID, PARENT_ID)));

        applyReply();

        verify(ssePublisher).publishCommentEvent(
                eq(PLANNER_ID), eq(SseEventType.COMMENT_ADDED), any(), eq(WRITER_ID), any());
    }

    @Test
    @DisplayName("a reply on an unpublished planner does not notify a parent author who is not the owner")
    void replyReceived_WhenPlannerUnpublishedAndParentAuthorIsNotTheOwner_NotifiesNobody() {
        givenUnpublished();
        givenAReplyUnder(PARENT_AUTHOR_ID);

        applyReply();

        verify(notificationDispatchService, never()).notifyReplyReceived(
                anyLong(), any(), any(), any(), any(), anyLong());
        verify(ssePublisher, never()).publishUserEvent(
                eq(PARENT_AUTHOR_ID), eq(SseEventType.NOTIFY_COMMENT), any(), any());
    }

    @Test
    @DisplayName("a reply on an unpublished planner still notifies the owner as parent author")
    void replyReceived_WhenPlannerUnpublishedAndParentAuthorIsTheOwner_NotifiesTheOwner() {
        givenUnpublished();
        givenAReplyUnder(OWNER_ID);
        when(notificationDispatchService.notifyReplyReceived(anyLong(), any(), any(), any(), any(), anyLong()))
                .thenReturn(delivered(OWNER_ID));

        applyReply();

        verify(notificationDispatchService).notifyReplyReceived(
                eq(REPLY_ID), any(), eq(PLANNER_ID), any(), any(), eq(OWNER_ID));
        verify(ssePublisher).publishUserEvent(eq(OWNER_ID), eq(SseEventType.NOTIFY_COMMENT), any(), any());
    }

    @Test
    @DisplayName("a reply on a published planner notifies a parent author who is not the owner")
    void replyReceived_WhenPlannerPublishedAndParentAuthorIsNotTheOwner_NotifiesTheParentAuthor() {
        givenAReplyUnder(PARENT_AUTHOR_ID);
        when(notificationDispatchService.notifyReplyReceived(anyLong(), any(), any(), any(), any(), anyLong()))
                .thenReturn(delivered(PARENT_AUTHOR_ID));

        applyReply();

        verify(notificationDispatchService).notifyReplyReceived(
                eq(REPLY_ID), any(), eq(PLANNER_ID), any(), any(), eq(PARENT_AUTHOR_ID));
        verify(ssePublisher).publishUserEvent(eq(PARENT_AUTHOR_ID), eq(SseEventType.NOTIFY_COMMENT), any(), any());
    }

    private void givenAReplyUnder(Long parentAuthorId) {
        when(commentRepository.findById(PARENT_ID)).thenReturn(Optional.of(comment(PARENT_ID, parentAuthorId, null)));
        when(commentRepository.findById(REPLY_ID))
                .thenReturn(Optional.of(comment(REPLY_ID, WRITER_ID, PARENT_ID)));
    }

    private static NotificationOutcome delivered(Long recipientId) {
        return new NotificationOutcome.Delivered(recipientId, new NotificationEventPayload(
                UUID.randomUUID().toString(), "COMMENT_RECEIVED", COMMENT_ID.toString(),
                Instant.now().toString(), PLANNER_ID.toString(), "Test Planner", "text", null));
    }

    private void applyReply() {
        ReplyReceivedEffect effect = new ReplyReceivedEffect(commentRepository, commentQueryService,
                plannerQueryService, notificationDispatchService, payloads);
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

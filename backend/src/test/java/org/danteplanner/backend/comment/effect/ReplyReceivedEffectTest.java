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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Who hears about a reply, on the same terms as {@link CommentReceivedEffectTest}: the recipient is
 * the parent's author, and the thread push happens either way.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    @Mock
    private CommentQueryService commentQueryService;

    @Mock
    private PublishedPlannerQueryService plannerQueryService;

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @Mock
    private SsePublisher ssePublisher;

    private ReplyReceivedEffect effect;

    @BeforeEach
    void setUp() {
        effect = new ReplyReceivedEffect(commentRepository, commentQueryService,
                plannerQueryService, notificationDispatchService,
                new DomainEventPayloadReader(new ObjectMapper()));
        when(plannerQueryService.notificationTargetOf(PLANNER_ID)).thenReturn(Optional.of(
                new PlannerNotificationTarget(PLANNER_ID, "Test Planner", OWNER_ID, true)));
    }

    @Test
    @DisplayName("the arm answers for REPLY_RECEIVED")
    void type_WhenAsked_IsReplyReceived() {
        assertThat(effect.type()).isEqualTo(DomainEventType.REPLY_RECEIVED);
    }

    @Test
    @DisplayName("an eligible parent author gets the notification and its push, plus the thread push")
    void applyEffect_WhenTheParentAuthorIsEligible_NotifiesAndPushesBoth() {
        givenAReplyTo(givenAParent(PARENT_AUTHOR_ID, true));
        when(notificationDispatchService.notifyReplyReceived(
                anyLong(), any(), any(), any(), any(), anyLong()))
                .thenReturn(delivered());

        applyAndFlush();

        verify(notificationDispatchService).notifyReplyReceived(
                eq(REPLY_ID), any(), eq(PLANNER_ID), eq("Test Planner"), eq("A reply"),
                eq(PARENT_AUTHOR_ID));
        verify(ssePublisher).publishUserEvent(
                eq(PARENT_AUTHOR_ID), eq(SseEventType.NOTIFY_COMMENT), any(), any());
        verify(ssePublisher).publishCommentEvent(
                eq(PLANNER_ID), eq(SseEventType.COMMENT_ADDED), any(), eq(REPLIER_ID), any());
    }

    @Test
    @DisplayName("a self-reply notifies nobody and still reaches the thread")
    void applyEffect_WhenTheReplierWroteTheParent_NotifiesNobodyButStillPushesTheThread() {
        givenAReplyTo(givenAParent(REPLIER_ID, true));

        applyAndFlush();

        verifyNoInteractions(notificationDispatchService);
        verify(ssePublisher, never()).publishUserEvent(any(), any(), any(), any());
        verify(ssePublisher).publishCommentEvent(
                eq(PLANNER_ID), eq(SseEventType.COMMENT_ADDED), any(), eq(REPLIER_ID), any());
    }

    @Test
    @DisplayName("a parent author who switched notifications off still gets the thread push")
    void applyEffect_WhenTheParentAuthorDisabledNotifications_NotifiesNobodyButStillPushesTheThread() {
        givenAReplyTo(givenAParent(PARENT_AUTHOR_ID, false));

        applyAndFlush();

        verifyNoInteractions(notificationDispatchService);
        verify(ssePublisher, never()).publishUserEvent(any(), any(), any(), any());
        verify(ssePublisher).publishCommentEvent(
                eq(PLANNER_ID), eq(SseEventType.COMMENT_ADDED), any(), eq(REPLIER_ID), any());
    }

    @Test
    @DisplayName("a reply withdrawn before dispatch announces nothing")
    void applyEffect_WhenTheReplyWasWithdrawn_AnnouncesNothing() {
        PlannerComment parent = givenAParent(PARENT_AUTHOR_ID, true);
        PlannerComment reply =
                new PlannerComment(PLANNER_ID, REPLIER_ID, "A reply", parent.getId(), 1);
        reply.setId(REPLY_ID);
        reply.setPublicId(UUID.randomUUID());
        reply.setCreatedAt(Instant.now());
        reply.softDelete();
        when(commentRepository.findById(REPLY_ID)).thenReturn(Optional.of(reply));

        applyAndFlush();

        verifyNoInteractions(notificationDispatchService, ssePublisher);
    }

    @Test
    @DisplayName("a reply deleted before dispatch announces nothing")
    void applyEffect_WhenTheReplyIsGone_AnnouncesNothing() {
        when(commentRepository.findById(REPLY_ID)).thenReturn(Optional.empty());

        applyAndFlush();

        verifyNoInteractions(notificationDispatchService, ssePublisher);
    }

    /**
     * Runs the arm and releases its queue, which is what the dispatch commit does in production.
     */
    private void applyAndFlush() {
        EffectPushQueue pushes = new EffectPushQueue(ssePublisher);
        effect.applyEffect(event(), pushes);
        pushes.flush();
    }

    private PlannerComment givenAParent(Long authorId, boolean authorNotificationsEnabled) {
        PlannerComment parent = new PlannerComment(PLANNER_ID, authorId, "Parent", null, 0);
        parent.setId(PARENT_ID);
        parent.setPublicId(PARENT_PUBLIC_ID);
        parent.setCreatedAt(Instant.now());
        parent.setAuthorNotificationsEnabled(authorNotificationsEnabled);
        when(commentRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent));
        return parent;
    }

    private void givenAReplyTo(PlannerComment parent) {
        PlannerComment reply =
                new PlannerComment(PLANNER_ID, REPLIER_ID, "A reply", parent.getId(), 1);
        reply.setId(REPLY_ID);
        reply.setPublicId(UUID.randomUUID());
        reply.setCreatedAt(Instant.now());
        when(commentRepository.findById(REPLY_ID)).thenReturn(Optional.of(reply));
    }

    private static NotificationOutcome delivered() {
        return new NotificationOutcome.Delivered(PARENT_AUTHOR_ID, new NotificationEventPayload(
                UUID.randomUUID().toString(), "REPLY_RECEIVED", REPLY_ID.toString(),
                Instant.now().toString(), PLANNER_ID.toString(), "Test Planner", "A reply", null));
    }

    private static DomainEvent event() {
        return DomainEvent.of(DomainEventType.REPLY_RECEIVED, PLANNER_ID,
                "{\"replyId\":" + REPLY_ID + "}");
    }
}

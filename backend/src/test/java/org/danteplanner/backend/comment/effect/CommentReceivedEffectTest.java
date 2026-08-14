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
 * Who hears about a top-level comment, decided where the rows that answer it are held.
 *
 * <p>The thread push is separated from the notification on purpose: a self-comment and a comment
 * whose recipient switched notifications off both still update every open thread, because that push
 * is how a reader's view stays current rather than a message addressed to anyone.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommentReceivedEffectTest {

    private static final UUID PLANNER_ID = UUID.randomUUID();
    private static final Long OWNER_ID = 1L;
    private static final Long COMMENTER_ID = 2L;
    private static final Long COMMENT_ID = 77L;

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

    private CommentReceivedEffect effect;

    @BeforeEach
    void setUp() {
        effect = new CommentReceivedEffect(commentRepository, commentQueryService,
                plannerQueryService, notificationDispatchService, ssePublisher,
                new DomainEventPayloadReader(new ObjectMapper()));
    }

    @Test
    @DisplayName("the arm answers for COMMENT_RECEIVED")
    void type_WhenAsked_IsCommentReceived() {
        assertThat(effect.type()).isEqualTo(DomainEventType.COMMENT_RECEIVED);
    }

    @Test
    @DisplayName("an eligible owner gets the notification and its push, plus the thread push")
    void applyEffect_WhenTheOwnerIsEligible_NotifiesAndPushesBoth() {
        givenAComment(COMMENTER_ID);
        givenAPlanner(true);
        when(notificationDispatchService.notifyCommentReceived(
                anyLong(), any(), any(), any(), any(), anyLong()))
                .thenReturn(delivered());

        effect.applyEffect(event());

        verify(notificationDispatchService).notifyCommentReceived(
                eq(COMMENT_ID), any(), eq(PLANNER_ID), eq("Test Planner"), eq("A comment"),
                eq(OWNER_ID));
        verify(ssePublisher).publishUserEvent(
                eq(OWNER_ID), eq(SseEventType.NOTIFY_COMMENT), any(), any());
        verify(ssePublisher).publishCommentEvent(
                eq(PLANNER_ID), eq(SseEventType.COMMENT_ADDED), any(), eq(COMMENTER_ID), any());
    }

    @Test
    @DisplayName("a self-comment notifies nobody and still reaches the thread")
    void applyEffect_WhenTheCommenterOwnsThePlanner_NotifiesNobodyButStillPushesTheThread() {
        givenAComment(OWNER_ID);
        givenAPlanner(true);

        effect.applyEffect(event());

        verifyNoInteractions(notificationDispatchService);
        verify(ssePublisher, never()).publishUserEvent(any(), any(), any(), any());
        verify(ssePublisher).publishCommentEvent(
                eq(PLANNER_ID), eq(SseEventType.COMMENT_ADDED), any(), eq(OWNER_ID), any());
    }

    @Test
    @DisplayName("an owner who switched notifications off still gets the thread push")
    void applyEffect_WhenTheOwnerDisabledNotifications_NotifiesNobodyButStillPushesTheThread() {
        givenAComment(COMMENTER_ID);
        givenAPlanner(false);

        effect.applyEffect(event());

        verifyNoInteractions(notificationDispatchService);
        verify(ssePublisher, never()).publishUserEvent(any(), any(), any(), any());
        verify(ssePublisher).publishCommentEvent(
                eq(PLANNER_ID), eq(SseEventType.COMMENT_ADDED), any(), eq(COMMENTER_ID), any());
    }

    @Test
    @DisplayName("a comment deleted before dispatch announces nothing")
    void applyEffect_WhenTheCommentIsGone_AnnouncesNothing() {
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

        effect.applyEffect(event());

        verifyNoInteractions(notificationDispatchService, ssePublisher);
    }

    @Test
    @DisplayName("a planner deleted before dispatch announces nothing")
    void applyEffect_WhenThePlannerIsGone_AnnouncesNothing() {
        givenAComment(COMMENTER_ID);
        when(plannerQueryService.notificationTargetOf(PLANNER_ID)).thenReturn(Optional.empty());

        effect.applyEffect(event());

        verifyNoInteractions(notificationDispatchService, ssePublisher);
    }

    private void givenAComment(Long authorId) {
        PlannerComment comment = new PlannerComment(PLANNER_ID, authorId, "A comment", null, 0);
        comment.setId(COMMENT_ID);
        comment.setPublicId(UUID.randomUUID());
        comment.setCreatedAt(Instant.now());
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
    }

    private void givenAPlanner(boolean ownerNotificationsEnabled) {
        when(plannerQueryService.notificationTargetOf(PLANNER_ID)).thenReturn(Optional.of(
                new PlannerNotificationTarget(PLANNER_ID, "Test Planner", OWNER_ID,
                        ownerNotificationsEnabled)));
    }

    private static NotificationOutcome delivered() {
        return new NotificationOutcome.Delivered(OWNER_ID, new NotificationEventPayload(
                UUID.randomUUID().toString(), "COMMENT_RECEIVED", COMMENT_ID.toString(),
                Instant.now().toString(), PLANNER_ID.toString(), "Test Planner", "A comment", null));
    }

    private static DomainEvent event() {
        return DomainEvent.of(DomainEventType.COMMENT_RECEIVED, PLANNER_ID,
                "{\"commentId\":" + COMMENT_ID + "}");
    }
}

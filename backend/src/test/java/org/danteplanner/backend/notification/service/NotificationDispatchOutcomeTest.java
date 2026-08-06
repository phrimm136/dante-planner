package org.danteplanner.backend.notification.service;

import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.entity.NotificationType;
import org.danteplanner.backend.notification.event.NotificationRaisedEvent;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.shared.entity.SseEventType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins duplicate suppression as a value rather than a caught exception.
 *
 * <p>The reachable duplicate is a planner re-crossing the recommendation threshold, dispatched from
 * an after-commit listener in its own REQUIRES_NEW transaction. Since comment and reply dispatch
 * began joining their caller's transaction, a constraint violation fired to signal a duplicate would
 * mark that shared transaction rollback-only, and the swallow would surface as an
 * {@code UnexpectedRollbackException} at the caller instead of suppressing anything.</p>
 *
 * <p>That nothing is caught is asserted as the absence of the attempt: the duplicate case never
 * reaches {@code save}, so no violation is fired for a catch block to swallow.</p>
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatchOutcomeTest {

    private static final Long RECIPIENT_ID = 4242L;

    private static final UUID PLANNER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static final UUID SAVED_PUBLIC_ID = UUID.fromString("99999999-8888-7777-6666-555555555555");

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NotificationDispatchService dispatchService;

    @Test
    @DisplayName("an already-notified recipient dispatches as Duplicate, writing nothing")
    void dispatch_WhenTheRecipientAlreadyCarriesTheNotification_YieldsDuplicateAndWritesNothing() {
        when(notificationRepository.existsByUserIdAndContentIdAndNotificationType(
                RECIPIENT_ID, PLANNER_ID.toString(), NotificationType.PLANNER_RECOMMENDED))
                .thenReturn(true);

        NotificationOutcome outcome =
                dispatchService.dispatch(recommendation(), SseEventType.NOTIFY_RECOMMENDED);

        assertThat(outcome).isEqualTo(new NotificationOutcome.Duplicate(
                RECIPIENT_ID, PLANNER_ID.toString(), NotificationType.PLANNER_RECOMMENDED));
        verify(notificationRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("a first-time notification dispatches as Delivered and queues its push")
    void dispatch_WhenTheRecipientCarriesNoSuchNotification_YieldsDeliveredAndQueuesThePush() {
        Notification notification = recommendation();
        when(notificationRepository.existsByUserIdAndContentIdAndNotificationType(
                RECIPIENT_ID, PLANNER_ID.toString(), NotificationType.PLANNER_RECOMMENDED))
                .thenReturn(false);
        when(notificationRepository.save(notification)).thenReturn(persisted(notification));

        NotificationOutcome outcome =
                dispatchService.dispatch(notification, SseEventType.NOTIFY_RECOMMENDED);

        assertThat(outcome).isEqualTo(new NotificationOutcome.Delivered(SAVED_PUBLIC_ID));
        verify(eventPublisher).publishEvent(any(NotificationRaisedEvent.class));
    }

    private static Notification recommendation() {
        return new Notification(
                RECIPIENT_ID,
                PLANNER_ID.toString(),
                NotificationType.PLANNER_RECOMMENDED,
                PLANNER_ID,
                "A planner that crossed the threshold",
                null,
                null);
    }

    /** The same notification as the persistence callbacks would hand it back. */
    private static Notification persisted(Notification notification) {
        notification.setPublicId(SAVED_PUBLIC_ID);
        notification.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return notification;
    }
}

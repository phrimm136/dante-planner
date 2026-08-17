package org.danteplanner.backend.notification.service;

import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.entity.NotificationType;
import org.danteplanner.backend.notification.repository.NotificationRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins duplicate suppression as a value the constraint decides, rather than a preceding check.
 *
 * <p>A dispatch replayed by the relay re-runs the same statement, and the row it would duplicate is
 * refused by {@code uk_notification_dedup} rather than by an existence read that a concurrent
 * writer could have raced. The suppressed case is asserted as the absence of the read-back: nothing
 * was written, so there is nothing to announce.</p>
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatchOutcomeTest {

    private static final Long RECIPIENT_ID = 4242L;

    private static final UUID PLANNER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static final UUID SAVED_PUBLIC_ID = UUID.fromString("99999999-8888-7777-6666-555555555555");

    private static final String TITLE = "A planner that crossed the threshold";

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationDispatchService dispatchService;

    @Test
    @DisplayName("a raise the dedup key refuses yields Duplicate and reads nothing back")
    void raise_WhenTheDedupKeyIsAlreadyOccupied_YieldsDuplicateAndReadsNothingBack() {
        whenInsertIgnoreYields(0);

        NotificationOutcome outcome =
                dispatchService.notifyPlannerRecommended(PLANNER_ID, TITLE, RECIPIENT_ID);

        assertThat(outcome).isEqualTo(new NotificationOutcome.Duplicate(
                RECIPIENT_ID, PLANNER_ID.toString(), NotificationType.PLANNER_RECOMMENDED));
        verify(notificationRepository, never())
                .findByUserIdAndContentIdAndNotificationType(any(), any(), any());
    }

    @Test
    @DisplayName("a raise the dedup key accepts yields Delivered carrying the recipient's payload")
    void raise_WhenTheDedupKeyIsFree_YieldsDeliveredCarryingTheRecipientsPayload() {
        whenInsertIgnoreYields(1);
        when(notificationRepository.findByUserIdAndContentIdAndNotificationType(
                RECIPIENT_ID, PLANNER_ID.toString(), NotificationType.PLANNER_RECOMMENDED))
                .thenReturn(Optional.of(persisted()));

        NotificationOutcome outcome =
                dispatchService.notifyPlannerRecommended(PLANNER_ID, TITLE, RECIPIENT_ID);

        assertThat(outcome).isInstanceOfSatisfying(NotificationOutcome.Delivered.class, delivered -> {
            assertThat(delivered.userId()).isEqualTo(RECIPIENT_ID);
            assertThat(delivered.payload().id()).isEqualTo(SAVED_PUBLIC_ID.toString());
            assertThat(delivered.payload().type())
                    .isEqualTo(NotificationType.PLANNER_RECOMMENDED.name());
        });
    }

    private void whenInsertIgnoreYields(int rows) {
        when(notificationRepository.insertIgnore(
                eq(RECIPIENT_ID),
                eq(PLANNER_ID.toString()),
                eq(NotificationType.PLANNER_RECOMMENDED.name()),
                eq(PLANNER_ID.toString()),
                eq(TITLE),
                isNull(),
                isNull()))
                .thenReturn(rows);
    }

    /** The row as the persistence callbacks would hand it back. */
    private static Notification persisted() {
        Notification notification = new Notification(
                RECIPIENT_ID,
                PLANNER_ID.toString(),
                NotificationType.PLANNER_RECOMMENDED,
                PLANNER_ID,
                TITLE,
                null,
                null);
        notification.setPublicId(SAVED_PUBLIC_ID);
        notification.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return notification;
    }
}

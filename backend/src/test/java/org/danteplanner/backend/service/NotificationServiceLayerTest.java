package org.danteplanner.backend.service;
import org.danteplanner.backend.shared.exception.EntityNotFoundException;

import org.danteplanner.backend.notification.service.NotificationRetentionService;
import org.danteplanner.backend.notification.service.NotificationDispatchService;
import org.danteplanner.backend.notification.service.NotificationInboxService;
import org.danteplanner.backend.notification.service.NotificationOutcome;

import org.danteplanner.backend.notification.dto.NotificationInboxResponse;
import org.danteplanner.backend.notification.dto.NotificationResponse;
import org.danteplanner.backend.notification.dto.UnreadCountResponse;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.entity.NotificationType;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the notification service layer.
 * Tests notification creation, retrieval, and cleanup logic.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceLayerTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationInboxService inboxService;
    private NotificationDispatchService dispatchService;
    private NotificationRetentionService retentionService;

    private Long testUserId = 100L;
    private UUID testPlannerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        inboxService = new NotificationInboxService(notificationRepository);
        dispatchService = new NotificationDispatchService(notificationRepository);
        retentionService = new NotificationRetentionService(notificationRepository);
    }

    private void whenInsertIgnoreYields(int rows) {
        when(notificationRepository.insertIgnore(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(rows);
    }

    /**
     * Stands the read-back row up as the persistence callbacks would hand it back.
     *
     * @return the public UUID the row came back carrying
     */
    private UUID whenReadBackYields(
            Long userId, String contentId, NotificationType type, String plannerTitle) {
        Notification stored = new Notification(
                userId, contentId, type, testPlannerId, plannerTitle, null, null);
        UUID publicId = UUID.randomUUID();
        stored.setPublicId(publicId);
        stored.setCreatedAt(Instant.now());
        lenient().when(notificationRepository.findByUserIdAndContentIdAndNotificationType(
                userId, contentId, type)).thenReturn(Optional.of(stored));
        return publicId;
    }

    @Nested
    @DisplayName("notifyPlannerPublished Tests")
    class NotifyPlannerPublishedTests {

        @Test
        void publishFanoutSingleStatement_WhenSubscribersExist_IssuesOneInsertSelectNotNInserts() {
            String title = "Fanout Build";

            dispatchService.notifyPlannerPublished(testUserId, testPlannerId, title);

            // The recipient filter runs inside the INSERT ... SELECT, so the rows the fan-out
            // produces are observable only in a containerized test against the migrated schema.
            verify(notificationRepository)
                    .insertPublishedFanout(testUserId, testPlannerId.toString(), title);
            verify(notificationRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("notifyPlannerRecommended Tests")
    class NotifyPlannerRecommendedTests {

        @Test
        @DisplayName("Should derive a PLANNER_RECOMMENDED row on the dedup key")
        void notifyPlannerRecommended_WhenValid_CreatesNotification() {
            // Arrange
            whenInsertIgnoreYields(1);
            whenReadBackYields(testUserId, testPlannerId.toString(),
                    NotificationType.PLANNER_RECOMMENDED, "Test Planner Title");

            // Act
            dispatchService.notifyPlannerRecommended(testPlannerId, "Test Planner Title", testUserId);

            // Assert
            verify(notificationRepository).insertIgnore(
                    testUserId,
                    testPlannerId.toString(),
                    NotificationType.PLANNER_RECOMMENDED.name(),
                    testPlannerId.toString(),
                    "Test Planner Title",
                    null,
                    null);
        }

        @Test
        @DisplayName("Should read nothing back when the dedup key refused the row")
        void notifyPlannerRecommended_WhenAlreadyNotified_WritesNothing() {
            // Arrange
            whenInsertIgnoreYields(0);

            // Act
            NotificationOutcome outcome = dispatchService
                    .notifyPlannerRecommended(testPlannerId, "Test Planner Title", testUserId);

            // Assert - the constraint decides, so nothing precedes the statement and nothing follows it
            assertInstanceOf(NotificationOutcome.Duplicate.class, outcome);
            verify(notificationRepository, never())
                    .findByUserIdAndContentIdAndNotificationType(any(), any(), any());
        }

        @Test
        @DisplayName("Should hand the arm the written row's payload on success")
        void notifyPlannerRecommended_WhenSuccess_CarriesTheWrittenPayload() {
            // Arrange
            whenInsertIgnoreYields(1);
            UUID publicId = whenReadBackYields(testUserId, testPlannerId.toString(),
                    NotificationType.PLANNER_RECOMMENDED, "Test Planner Title");

            // Act
            NotificationOutcome outcome = dispatchService
                    .notifyPlannerRecommended(testPlannerId, "Test Planner Title", testUserId);

            // Assert
            NotificationOutcome.Delivered delivered =
                    assertInstanceOf(NotificationOutcome.Delivered.class, outcome);
            assertEquals(testUserId, delivered.userId());
            assertEquals(publicId.toString(), delivered.payload().id());
            assertEquals(NotificationType.PLANNER_RECOMMENDED.name(), delivered.payload().type());
            assertEquals(testPlannerId.toString(), delivered.payload().contentId());
            assertEquals(testPlannerId.toString(), delivered.payload().plannerId());
            assertEquals("Test Planner Title", delivered.payload().plannerTitle());
        }
    }

    @Nested
    @DisplayName("notifyCommentReceived Tests")
    class NotifyCommentReceivedTests {

        @Test
        @DisplayName("Should derive a COMMENT_RECEIVED row keyed on the comment")
        void notifyCommentReceived_WhenRaised_CreatesNotification() {
            // Arrange
            Long plannerOwnerId = 100L;
            Long commentId = 999L;
            UUID commentPublicId = UUID.randomUUID();
            whenInsertIgnoreYields(1);
            whenReadBackYields(plannerOwnerId, commentId.toString(),
                    NotificationType.COMMENT_RECEIVED, "Test Planner");

            // Act
            dispatchService.notifyCommentReceived(
                    commentId, commentPublicId, testPlannerId, "Test Planner",
                    "Test content", plannerOwnerId);

            // Assert
            verify(notificationRepository).insertIgnore(
                    plannerOwnerId,
                    commentId.toString(),
                    NotificationType.COMMENT_RECEIVED.name(),
                    testPlannerId.toString(),
                    "Test Planner",
                    "Test content",
                    commentPublicId.toString());
        }

        @Test
        @DisplayName("Should read nothing back when the dedup key refused the row")
        void notifyCommentReceived_WhenAlreadyNotified_WritesNothing() {
            // Arrange
            Long plannerOwnerId = 100L;
            Long commentId = 999L;
            UUID commentPublicId = UUID.randomUUID();
            whenInsertIgnoreYields(0);

            // Act
            NotificationOutcome outcome = dispatchService.notifyCommentReceived(
                    commentId, commentPublicId, testPlannerId, "Test Planner",
                    "Test content", plannerOwnerId);

            // Assert
            assertInstanceOf(NotificationOutcome.Duplicate.class, outcome);
            verify(notificationRepository, never())
                    .findByUserIdAndContentIdAndNotificationType(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("notifyReplyReceived Tests")
    class NotifyReplyReceivedTests {

        @Test
        @DisplayName("Should derive a REPLY_RECEIVED row keyed on the reply")
        void notifyReplyReceived_WhenRaised_CreatesNotification() {
            // Arrange
            Long replyId = 101L;
            UUID replyPublicId = UUID.randomUUID();
            Long parentAuthorId = 100L;
            whenInsertIgnoreYields(1);
            whenReadBackYields(parentAuthorId, replyId.toString(),
                    NotificationType.REPLY_RECEIVED, "Test Planner");

            // Act
            dispatchService.notifyReplyReceived(
                    replyId, replyPublicId, testPlannerId, "Test Planner",
                    "Reply content", parentAuthorId);

            // Assert
            verify(notificationRepository).insertIgnore(
                    parentAuthorId,
                    replyId.toString(),
                    NotificationType.REPLY_RECEIVED.name(),
                    testPlannerId.toString(),
                    "Test Planner",
                    "Reply content",
                    replyPublicId.toString());
        }
    }

    @Nested
    @DisplayName("getInbox Tests")
    class GetInboxTests {

        @Test
        @DisplayName("Should return paginated notifications")
        void getInbox_WhenNotificationsExist_ReturnsPagedResults() {
            // Arrange
            Notification n1 = new Notification(testUserId, testPlannerId.toString(), NotificationType.PLANNER_RECOMMENDED);
            Notification n2 = new Notification(testUserId, testPlannerId.toString(), NotificationType.COMMENT_RECEIVED);

            Page<Notification> page = new PageImpl<>(List.of(n1, n2));

            when(notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(eq(testUserId), any(Pageable.class)))
                    .thenReturn(page);

            // Act
            NotificationInboxResponse response = inboxService.getInbox(testUserId, 0, 20);

            // Assert
            assertEquals(2, response.notifications().size());
            assertEquals(0, response.page());
            assertEquals(2, response.size()); // actual size, not requested size
            assertEquals(2, response.totalElements());
        }

        @Test
        @DisplayName("Should return empty page when no notifications")
        void getInbox_WhenNoNotifications_ReturnsEmpty() {
            // Arrange
            Page<Notification> emptyPage = new PageImpl<>(List.of());

            when(notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(eq(testUserId), any(Pageable.class)))
                    .thenReturn(emptyPage);

            // Act
            NotificationInboxResponse response = inboxService.getInbox(testUserId, 0, 20);

            // Assert
            assertTrue(response.notifications().isEmpty());
            assertEquals(0, response.totalElements());
        }
    }

    @Nested
    @DisplayName("getUnreadCount Tests")
    class GetUnreadCountTests {

        @Test
        @DisplayName("Should return correct unread count")
        void getUnreadCount_WhenUnreadExist_ReturnsCount() {
            // Arrange
            when(notificationRepository.countByUserIdAndReadFalseAndDeletedAtIsNull(testUserId))
                    .thenReturn(5L);

            // Act
            UnreadCountResponse response = inboxService.getUnreadCount(testUserId);

            // Assert
            assertEquals(5L, response.unreadCount());
        }

        @Test
        @DisplayName("Should return 0 when no unread notifications")
        void getUnreadCount_WhenNoUnread_ReturnsZero() {
            // Arrange
            when(notificationRepository.countByUserIdAndReadFalseAndDeletedAtIsNull(testUserId))
                    .thenReturn(0L);

            // Act
            UnreadCountResponse response = inboxService.getUnreadCount(testUserId);

            // Assert
            assertEquals(0L, response.unreadCount());
        }
    }

    @Nested
    @DisplayName("markAsRead Tests")
    class MarkAsReadTests {

        @Test
        @DisplayName("Should mark notification as read and set readAt timestamp")
        void markAsRead_WhenSuccess_SetsReadFlag() {
            // Arrange
            UUID publicId = UUID.randomUUID();
            Notification notification = new Notification(testUserId, testPlannerId.toString(), NotificationType.PLANNER_RECOMMENDED);
            notification.setPublicId(publicId);
            assertFalse(notification.isRead());

            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.of(notification));

            // Act
            NotificationResponse response = inboxService.markAsRead(publicId, testUserId);

            // Assert
            assertTrue(notification.isRead());
            assertNotNull(notification.getReadAt());
            assertTrue(response.read());
        }

        @Test
        @DisplayName("Should throw exception when notification not found")
        void markAsRead_WhenNotFound_ThrowsException() {
            // Arrange
            UUID publicId = UUID.randomUUID();
            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(EntityNotFoundException.class,
                    () -> inboxService.markAsRead(publicId, testUserId));
        }

        @Test
        @DisplayName("Should throw exception when notification does not belong to user")
        void markAsRead_WhenWrongUser_ThrowsException() {
            // Arrange
            UUID publicId = UUID.randomUUID();
            Notification notification = new Notification(100L, testPlannerId.toString(), NotificationType.PLANNER_RECOMMENDED);
            notification.setPublicId(publicId);

            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.of(notification));

            // Act & Assert
            assertThrows(EntityNotFoundException.class,
                    () -> inboxService.markAsRead(publicId, 999L));
        }
    }

    @Nested
    @DisplayName("markAllAsRead Tests")
    class MarkAllAsReadTests {

        @Test
        @DisplayName("Should mark all unread notifications as read")
        void markAllAsRead_WhenUnreadExist_ReturnsUpdatedCount() {
            // Arrange
            when(notificationRepository.markAllAsRead(eq(testUserId), any(Instant.class)))
                    .thenReturn(3);

            // Act
            int count = inboxService.markAllAsRead(testUserId);

            // Assert
            assertEquals(3, count);
        }

        @Test
        @DisplayName("Should return 0 when no unread notifications")
        void markAllAsRead_WhenNoUnread_ReturnsZero() {
            // Arrange
            when(notificationRepository.markAllAsRead(eq(testUserId), any(Instant.class)))
                    .thenReturn(0);

            // Act
            int count = inboxService.markAllAsRead(testUserId);

            // Assert
            assertEquals(0, count);
        }
    }

    @Nested
    @DisplayName("deleteNotification Tests")
    class DeleteNotificationTests {

        @Test
        @DisplayName("Should soft-delete notification successfully")
        void deleteNotification_WhenSuccess_SoftDeletes() {
            // Arrange
            UUID publicId = UUID.randomUUID();
            Notification notification = new Notification(testUserId, testPlannerId.toString(), NotificationType.PLANNER_RECOMMENDED);
            notification.setPublicId(publicId);
            assertFalse(notification.isDeleted());

            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.of(notification));

            // Act
            inboxService.deleteNotification(publicId, testUserId);

            // Assert
            assertTrue(notification.isDeleted());
            assertNotNull(notification.getDeletedAt());
        }

        @Test
        @DisplayName("Should throw exception when notification not found")
        void deleteNotification_WhenNotFound_ThrowsException() {
            // Arrange
            UUID publicId = UUID.randomUUID();
            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(EntityNotFoundException.class,
                    () -> inboxService.deleteNotification(publicId, testUserId));
        }

        @Test
        @DisplayName("Should throw exception when notification does not belong to user")
        void deleteNotification_WhenWrongUser_ThrowsException() {
            // Arrange
            UUID publicId = UUID.randomUUID();
            Notification notification = new Notification(100L, testPlannerId.toString(), NotificationType.PLANNER_RECOMMENDED);
            notification.setPublicId(publicId);

            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.of(notification));

            // Act & Assert
            assertThrows(EntityNotFoundException.class,
                    () -> inboxService.deleteNotification(publicId, 999L));
        }
    }

    @Nested
    @DisplayName("cleanupOldNotifications Tests")
    class CleanupOldNotificationsTests {

        @Test
        @DisplayName("Should soft-delete old read notifications and hard-delete old soft-deleted")
        void cleanupOldNotifications_WhenRun_SoftAndHardDeletes() {
            // Arrange
            when(notificationRepository.softDeleteOldReadNotifications(any(Instant.class), any(Instant.class)))
                    .thenReturn(10);
            when(notificationRepository.hardDeleteOldNotifications(any(Instant.class)))
                    .thenReturn(5);

            // Act
            retentionService.purgeExpired();

            // Assert
            // Which rows the two sweeps remove is observable only in a containerized test seeded
            // with read and soft-deleted rows on both sides of the cutoffs.
            verify(notificationRepository).softDeleteOldReadNotifications(any(Instant.class), any(Instant.class));
            verify(notificationRepository).hardDeleteOldNotifications(any(Instant.class));
        }

        @Test
        @DisplayName("Should use correct cutoff dates")
        void cleanupOldNotifications_WhenRun_UsesCorrectCutoffDates() {
            // Arrange
            ArgumentCaptor<Instant> softDeleteCutoffCaptor = ArgumentCaptor.forClass(Instant.class);
            ArgumentCaptor<Instant> hardDeleteCutoffCaptor = ArgumentCaptor.forClass(Instant.class);

            when(notificationRepository.softDeleteOldReadNotifications(softDeleteCutoffCaptor.capture(), any(Instant.class)))
                    .thenReturn(0);
            when(notificationRepository.hardDeleteOldNotifications(hardDeleteCutoffCaptor.capture()))
                    .thenReturn(0);

            // Act
            retentionService.purgeExpired();

            // Assert - verify cutoff dates are approximately correct
            // The 90/365-day retention window is only observable as the cutoff arguments here;
            // asserting which rows survive it needs a containerized test with aged rows.
            Instant now = Instant.now();
            Instant softDeleteCutoff = softDeleteCutoffCaptor.getValue();
            Instant hardDeleteCutoff = hardDeleteCutoffCaptor.getValue();

            long softDeleteDays = ChronoUnit.DAYS.between(softDeleteCutoff, now);
            long hardDeleteDays = ChronoUnit.DAYS.between(hardDeleteCutoff, now);

            assertTrue(softDeleteDays >= 89 && softDeleteDays <= 91, "Soft delete cutoff should be ~90 days");
            assertTrue(hardDeleteDays >= 364 && hardDeleteDays <= 366, "Hard delete cutoff should be ~365 days");
        }
    }
}

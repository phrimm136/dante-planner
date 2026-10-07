package org.danteplanner.backend.service;

import org.danteplanner.backend.notification.service.NotificationRetentionService;
import org.danteplanner.backend.notification.service.NotificationDispatchService;
import org.danteplanner.backend.notification.service.NotificationInboxService;
import org.danteplanner.backend.notification.api.NotificationOutcome;

import org.danteplanner.backend.notification.dto.NotificationInboxResponse;
import org.danteplanner.backend.notification.dto.NotificationResponse;
import org.danteplanner.backend.notification.dto.UnreadCountResponse;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.api.NotificationType;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;

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
    class NotifyPlannerPublishedTests {

        @Test
        void publishFanoutSingleStatement_WhenSubscribersExist_IssuesOneInsertSelectNotNInserts() {
            String title = "Fanout Build";

            dispatchService.notifyPlannerPublished(testUserId, testPlannerId, title);

            verify(notificationRepository)
                    .insertPublishedFanout(testUserId, testPlannerId.toString(), title);
            verify(notificationRepository, never()).saveAll(any());
        }
    }

    @Nested
    class NotifyPlannerRecommendedTests {

        @Test
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
        void notifyPlannerRecommended_WhenAlreadyNotified_WritesNothing() {
            // Arrange
            whenInsertIgnoreYields(0);

            // Act
            NotificationOutcome outcome = dispatchService
                    .notifyPlannerRecommended(testPlannerId, "Test Planner Title", testUserId);

            // Assert - the constraint decides, so nothing precedes the statement and nothing follows it
            assertThat(outcome).isInstanceOf(NotificationOutcome.Suppressed.class);
            verify(notificationRepository, never())
                    .findByUserIdAndContentIdAndNotificationType(any(), any(), any());
        }

        @Test
        void notifyPlannerRecommended_WhenSuccess_CarriesTheWrittenPayload() {
            // Arrange
            whenInsertIgnoreYields(1);
            UUID publicId = whenReadBackYields(testUserId, testPlannerId.toString(),
                    NotificationType.PLANNER_RECOMMENDED, "Test Planner Title");

            // Act
            NotificationOutcome outcome = dispatchService
                    .notifyPlannerRecommended(testPlannerId, "Test Planner Title", testUserId);

            // Assert
            assertThat(outcome).isInstanceOfSatisfying(NotificationOutcome.Delivered.class, delivered -> {
                assertThat(delivered.userId()).isEqualTo(testUserId);
                assertThat(delivered.payload().id()).isEqualTo(publicId.toString());
                assertThat(delivered.payload().type()).isEqualTo(NotificationType.PLANNER_RECOMMENDED.name());
                assertThat(delivered.payload().contentId()).isEqualTo(testPlannerId.toString());
                assertThat(delivered.payload().plannerId()).isEqualTo(testPlannerId.toString());
                assertThat(delivered.payload().plannerTitle()).isEqualTo("Test Planner Title");
            });
        }
    }

    @Nested
    class NotifyCommentReceivedTests {

        @Test
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
            assertThat(outcome).isInstanceOf(NotificationOutcome.Suppressed.class);
            verify(notificationRepository, never())
                    .findByUserIdAndContentIdAndNotificationType(any(), any(), any());
        }
    }

    @Nested
    class NotifyReplyReceivedTests {

        @Test
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
    class GetInboxTests {

        @Test
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
            assertThat(response.notifications().size()).isEqualTo(2);
            assertThat(response.page()).isEqualTo(0);
            assertThat(response.size()).isEqualTo(2); // actual size, not requested size
            assertThat(response.totalElements()).isEqualTo(2);
        }

        @Test
        void getInbox_WhenNoNotifications_ReturnsEmpty() {
            // Arrange
            Page<Notification> emptyPage = new PageImpl<>(List.of());

            when(notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(eq(testUserId), any(Pageable.class)))
                    .thenReturn(emptyPage);

            // Act
            NotificationInboxResponse response = inboxService.getInbox(testUserId, 0, 20);

            // Assert
            assertThat(response.notifications()).isEmpty();
            assertThat(response.totalElements()).isEqualTo(0);
        }
    }

    @Nested
    class GetUnreadCountTests {

        @Test
        void getUnreadCount_WhenUnreadExist_ReturnsCount() {
            // Arrange
            when(notificationRepository.countByUserIdAndReadFalseAndDeletedAtIsNull(testUserId))
                    .thenReturn(5L);

            // Act
            UnreadCountResponse response = inboxService.getUnreadCount(testUserId);

            // Assert
            assertThat(response.unreadCount()).isEqualTo(5L);
        }

        @Test
        void getUnreadCount_WhenNoUnread_ReturnsZero() {
            // Arrange
            when(notificationRepository.countByUserIdAndReadFalseAndDeletedAtIsNull(testUserId))
                    .thenReturn(0L);

            // Act
            UnreadCountResponse response = inboxService.getUnreadCount(testUserId);

            // Assert
            assertThat(response.unreadCount()).isEqualTo(0L);
        }
    }

    @Nested
    class MarkAsReadTests {

        @Test
        void markAsRead_WhenSuccess_SetsReadFlag() {
            // Arrange
            UUID publicId = UUID.randomUUID();
            Notification notification = new Notification(testUserId, testPlannerId.toString(), NotificationType.PLANNER_RECOMMENDED);
            notification.setPublicId(publicId);
            assertThat(notification.isRead()).isFalse();

            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.of(notification));

            // Act
            NotificationResponse response = inboxService.markAsRead(publicId, testUserId);

            // Assert
            assertThat(notification.isRead()).isTrue();
            assertThat(notification.getReadAt()).isNotNull();
            assertThat(response.read()).isTrue();
        }

        @Test
        void markAsRead_WhenNotFound_ThrowsException() {
            // Arrange
            UUID publicId = UUID.randomUUID();
            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertRejected(() -> inboxService.markAsRead(publicId, testUserId))
                    .withCode("NOT_FOUND")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).isEqualTo("Notification not found with id: " + publicId));
        }

        @Test
        void markAsRead_WhenWrongUser_ThrowsException() {
            // Arrange
            UUID publicId = UUID.randomUUID();
            Notification notification = new Notification(100L, testPlannerId.toString(), NotificationType.PLANNER_RECOMMENDED);
            notification.setPublicId(publicId);

            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.of(notification));

            // Act & Assert
            assertRejected(() -> inboxService.markAsRead(publicId, 999L))
                    .withCode("NOT_FOUND")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).isEqualTo("Notification not found with id: " + publicId));
        }
    }

    @Nested
    class MarkAllAsReadTests {

        @Test
        void markAllAsRead_WhenUnreadExist_ReturnsUpdatedCount() {
            // Arrange
            when(notificationRepository.markAllAsRead(eq(testUserId), any(Instant.class)))
                    .thenReturn(3);

            // Act
            int count = inboxService.markAllAsRead(testUserId);

            // Assert
            assertThat(count).isEqualTo(3);
        }

        @Test
        void markAllAsRead_WhenNoUnread_ReturnsZero() {
            // Arrange
            when(notificationRepository.markAllAsRead(eq(testUserId), any(Instant.class)))
                    .thenReturn(0);

            // Act
            int count = inboxService.markAllAsRead(testUserId);

            // Assert
            assertThat(count).isEqualTo(0);
        }
    }

    @Nested
    class DeleteNotificationTests {

        @Test
        void deleteNotification_WhenSuccess_SoftDeletes() {
            // Arrange
            UUID publicId = UUID.randomUUID();
            Notification notification = new Notification(testUserId, testPlannerId.toString(), NotificationType.PLANNER_RECOMMENDED);
            notification.setPublicId(publicId);
            assertThat(notification.isDeleted()).isFalse();

            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.of(notification));

            // Act
            inboxService.deleteNotification(publicId, testUserId);

            // Assert
            assertThat(notification.isDeleted()).isTrue();
            assertThat(notification.getDeletedAt()).isNotNull();
        }

        @Test
        void deleteNotification_WhenNotFound_ThrowsException() {
            // Arrange
            UUID publicId = UUID.randomUUID();
            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertRejected(() -> inboxService.deleteNotification(publicId, testUserId))
                    .withCode("NOT_FOUND")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).isEqualTo("Notification not found with id: " + publicId));
        }

        @Test
        void deleteNotification_WhenWrongUser_ThrowsException() {
            // Arrange
            UUID publicId = UUID.randomUUID();
            Notification notification = new Notification(100L, testPlannerId.toString(), NotificationType.PLANNER_RECOMMENDED);
            notification.setPublicId(publicId);

            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.of(notification));

            // Act & Assert
            assertRejected(() -> inboxService.deleteNotification(publicId, 999L))
                    .withCode("NOT_FOUND")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).isEqualTo("Notification not found with id: " + publicId));
        }
    }

    @Nested
    class CleanupOldNotificationsTests {

        @Test
        void cleanupOldNotifications_WhenRun_SoftAndHardDeletes() {
            // Arrange
            when(notificationRepository.softDeleteOldReadNotifications(any(Instant.class), any(Instant.class)))
                    .thenReturn(10);
            when(notificationRepository.hardDeleteOldNotifications(any(Instant.class)))
                    .thenReturn(5);

            // Act
            retentionService.purgeExpired();

            // Assert
            verify(notificationRepository).softDeleteOldReadNotifications(any(Instant.class), any(Instant.class));
            verify(notificationRepository).hardDeleteOldNotifications(any(Instant.class));
        }

        @Test
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
            Instant now = Instant.now();
            Instant softDeleteCutoff = softDeleteCutoffCaptor.getValue();
            Instant hardDeleteCutoff = hardDeleteCutoffCaptor.getValue();

            long softDeleteDays = ChronoUnit.DAYS.between(softDeleteCutoff, now);
            long hardDeleteDays = ChronoUnit.DAYS.between(hardDeleteCutoff, now);

            assertThat(softDeleteDays).as("Soft delete cutoff should be ~90 days").isBetween(89L, 91L);
            assertThat(hardDeleteDays).as("Hard delete cutoff should be ~365 days").isBetween(364L, 366L);
        }
    }
}

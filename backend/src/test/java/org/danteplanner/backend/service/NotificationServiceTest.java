package org.danteplanner.backend.service;

import org.danteplanner.backend.dto.planner.NotificationInboxResponse;
import org.danteplanner.backend.dto.planner.NotificationResponse;
import org.danteplanner.backend.dto.planner.UnreadCountResponse;
import org.danteplanner.backend.entity.Notification;
import org.danteplanner.backend.entity.NotificationType;
import org.danteplanner.backend.repository.NotificationRepository;
import org.danteplanner.backend.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
 * Unit tests for NotificationService.
 * Tests notification creation, retrieval, and cleanup logic.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SseService sseService;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    private NotificationService notificationService;

    private Long testUserId = 100L;
    private UUID testPlannerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, sseService, userSettingsRepository);
    }

    @Nested
    @DisplayName("notifyPlannerRecommended Tests")
    class NotifyPlannerRecommendedTests {

        @Test
        @DisplayName("Should create PLANNER_RECOMMENDED notification successfully")
        void notifyPlannerRecommended_Success() {
            // Arrange - set publicId and createdAt on saved notification (simulating @PrePersist)
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> {
                        Notification n = invocation.getArgument(0);
                        n.setPublicId(UUID.randomUUID());
                        n.setCreatedAt(Instant.now());
                        return n;
                    });

            // Act
            notificationService.notifyPlannerRecommended(testPlannerId, "Test Planner Title", testUserId);

            // Assert
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());

            Notification saved = captor.getValue();
            assertEquals(testUserId, saved.getUserId());
            assertEquals(testPlannerId.toString(), saved.getContentId());
            assertEquals(NotificationType.PLANNER_RECOMMENDED, saved.getNotificationType());
            assertEquals(testPlannerId, saved.getPlannerId());
            assertEquals("Test Planner Title", saved.getPlannerTitle());
            assertFalse(saved.getRead());
        }

        @Test
        @DisplayName("Should handle duplicate notification via UNIQUE constraint gracefully")
        void notifyPlannerRecommended_Duplicate_CatchesException() {
            // Arrange
            when(notificationRepository.save(any(Notification.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate key violation"));

            // Act & Assert - should not throw, logs debug message
            assertDoesNotThrow(() ->
                    notificationService.notifyPlannerRecommended(testPlannerId, "Test Planner Title", testUserId)
            );
        }
    }

    @Nested
    @DisplayName("notifyCommentReceived Tests")
    class NotifyCommentReceivedTests {

        @Test
        @DisplayName("Should create COMMENT_RECEIVED notification when commenter is not owner")
        void notifyCommentReceived_DifferentUser_CreatesNotification() {
            // Arrange
            Long plannerOwnerId = 100L;
            Long commenterId = 200L;
            Long commentId = 999L;
            UUID commentPublicId = UUID.randomUUID();

            // Set publicId and createdAt on saved notification (simulating @PrePersist)
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> {
                        Notification n = invocation.getArgument(0);
                        n.setPublicId(UUID.randomUUID());
                        n.setCreatedAt(Instant.now());
                        return n;
                    });

            // Act
            notificationService.notifyCommentReceived(
                    commentId, commentPublicId, testPlannerId, "Test Planner",
                    "Test content", plannerOwnerId, commenterId);

            // Assert
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());

            Notification saved = captor.getValue();
            assertEquals(plannerOwnerId, saved.getUserId());
            assertEquals(commentId.toString(), saved.getContentId());
            assertEquals(NotificationType.COMMENT_RECEIVED, saved.getNotificationType());
        }

        @Test
        @DisplayName("Should not notify when commenter is the planner owner")
        void notifyCommentReceived_SameUser_NoNotification() {
            // Arrange
            Long userId = 100L;
            Long commentId = 999L;
            UUID commentPublicId = UUID.randomUUID();

            // Act
            notificationService.notifyCommentReceived(
                    commentId, commentPublicId, testPlannerId, "Test Planner",
                    "Test content", userId, userId);

            // Assert
            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should handle duplicate notification via UNIQUE constraint")
        void notifyCommentReceived_Duplicate_CatchesException() {
            // Arrange
            Long plannerOwnerId = 100L;
            Long commenterId = 200L;
            Long commentId = 999L;
            UUID commentPublicId = UUID.randomUUID();

            when(notificationRepository.save(any(Notification.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate key"));

            // Act & Assert - should not throw
            assertDoesNotThrow(() ->
                    notificationService.notifyCommentReceived(
                            commentId, commentPublicId, testPlannerId, "Test Planner",
                            "Test content", plannerOwnerId, commenterId)
            );
        }
    }

    @Nested
    @DisplayName("notifyReplyReceived Tests")
    class NotifyReplyReceivedTests {

        @Test
        @DisplayName("Should create REPLY_RECEIVED notification when replier is not parent author")
        void notifyReplyReceived_DifferentUser_CreatesNotification() {
            // Arrange
            Long replyId = 101L;
            UUID replyPublicId = UUID.randomUUID();
            Long parentAuthorId = 100L;
            Long replierId = 200L;

            // Set publicId and createdAt on saved notification (simulating @PrePersist)
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> {
                        Notification n = invocation.getArgument(0);
                        n.setPublicId(UUID.randomUUID());
                        n.setCreatedAt(Instant.now());
                        return n;
                    });

            // Act
            notificationService.notifyReplyReceived(
                    replyId, replyPublicId, testPlannerId, "Test Planner",
                    "Reply content", parentAuthorId, replierId);

            // Assert
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());

            Notification saved = captor.getValue();
            assertEquals(parentAuthorId, saved.getUserId());
            assertEquals(replyId.toString(), saved.getContentId());
            assertEquals(NotificationType.REPLY_RECEIVED, saved.getNotificationType());
        }

        @Test
        @DisplayName("Should not notify when replier is the parent comment author")
        void notifyReplyReceived_SameUser_NoNotification() {
            // Arrange
            Long userId = 100L;
            Long replyId = 101L;
            UUID replyPublicId = UUID.randomUUID();

            // Act
            notificationService.notifyReplyReceived(
                    replyId, replyPublicId, testPlannerId, "Test Planner",
                    "Reply content", userId, userId);

            // Assert
            verify(notificationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getInbox Tests")
    class GetInboxTests {

        @Test
        @DisplayName("Should return paginated notifications")
        void getInbox_ReturnsPagedResults() {
            // Arrange
            Notification n1 = new Notification(testUserId, testPlannerId.toString(), NotificationType.PLANNER_RECOMMENDED);
            Notification n2 = new Notification(testUserId, testPlannerId.toString(), NotificationType.COMMENT_RECEIVED);

            Page<Notification> page = new PageImpl<>(List.of(n1, n2));

            when(notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(eq(testUserId), any(Pageable.class)))
                    .thenReturn(page);

            // Act
            NotificationInboxResponse response = notificationService.getInbox(testUserId, 0, 20);

            // Assert
            assertEquals(2, response.notifications().size());
            assertEquals(0, response.page());
            assertEquals(2, response.size()); // actual size, not requested size
            assertEquals(2, response.totalElements());
        }

        @Test
        @DisplayName("Should return empty page when no notifications")
        void getInbox_NoNotifications_ReturnsEmpty() {
            // Arrange
            Page<Notification> emptyPage = new PageImpl<>(List.of());

            when(notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(eq(testUserId), any(Pageable.class)))
                    .thenReturn(emptyPage);

            // Act
            NotificationInboxResponse response = notificationService.getInbox(testUserId, 0, 20);

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
        void getUnreadCount_ReturnsCount() {
            // Arrange
            when(notificationRepository.countByUserIdAndReadFalseAndDeletedAtIsNull(testUserId))
                    .thenReturn(5L);

            // Act
            UnreadCountResponse response = notificationService.getUnreadCount(testUserId);

            // Assert
            assertEquals(5L, response.unreadCount());
        }

        @Test
        @DisplayName("Should return 0 when no unread notifications")
        void getUnreadCount_NoUnread_ReturnsZero() {
            // Arrange
            when(notificationRepository.countByUserIdAndReadFalseAndDeletedAtIsNull(testUserId))
                    .thenReturn(0L);

            // Act
            UnreadCountResponse response = notificationService.getUnreadCount(testUserId);

            // Assert
            assertEquals(0L, response.unreadCount());
        }
    }

    @Nested
    @DisplayName("markAsRead Tests")
    class MarkAsReadTests {

        @Test
        @DisplayName("Should mark notification as read and set readAt timestamp")
        void markAsRead_Success_SetsReadFlag() {
            // Arrange
            UUID publicId = UUID.randomUUID();
            Notification notification = new Notification(testUserId, testPlannerId.toString(), NotificationType.PLANNER_RECOMMENDED);
            notification.setPublicId(publicId);
            assertFalse(notification.getRead());

            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.of(notification));
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            NotificationResponse response = notificationService.markAsRead(publicId, testUserId);

            // Assert
            assertTrue(notification.getRead());
            assertNotNull(notification.getReadAt());
            assertTrue(response.read());
        }

        @Test
        @DisplayName("Should throw exception when notification not found")
        void markAsRead_NotFound_ThrowsException() {
            // Arrange
            UUID publicId = UUID.randomUUID();
            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> notificationService.markAsRead(publicId, testUserId));
        }

        @Test
        @DisplayName("Should throw exception when notification does not belong to user")
        void markAsRead_WrongUser_ThrowsException() {
            // Arrange
            UUID publicId = UUID.randomUUID();
            Notification notification = new Notification(100L, testPlannerId.toString(), NotificationType.PLANNER_RECOMMENDED);
            notification.setPublicId(publicId);

            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.of(notification));

            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> notificationService.markAsRead(publicId, 999L));
        }
    }

    @Nested
    @DisplayName("markAllAsRead Tests")
    class MarkAllAsReadTests {

        @Test
        @DisplayName("Should mark all unread notifications as read")
        void markAllAsRead_ReturnsUpdatedCount() {
            // Arrange
            when(notificationRepository.markAllAsRead(eq(testUserId), any(Instant.class)))
                    .thenReturn(3);

            // Act
            int count = notificationService.markAllAsRead(testUserId);

            // Assert
            assertEquals(3, count);
        }

        @Test
        @DisplayName("Should return 0 when no unread notifications")
        void markAllAsRead_NoUnread_ReturnsZero() {
            // Arrange
            when(notificationRepository.markAllAsRead(eq(testUserId), any(Instant.class)))
                    .thenReturn(0);

            // Act
            int count = notificationService.markAllAsRead(testUserId);

            // Assert
            assertEquals(0, count);
        }
    }

    @Nested
    @DisplayName("deleteNotification Tests")
    class DeleteNotificationTests {

        @Test
        @DisplayName("Should soft-delete notification successfully")
        void deleteNotification_Success_SoftDeletes() {
            // Arrange
            UUID publicId = UUID.randomUUID();
            Notification notification = new Notification(testUserId, testPlannerId.toString(), NotificationType.PLANNER_RECOMMENDED);
            notification.setPublicId(publicId);
            assertFalse(notification.isDeleted());

            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.of(notification));
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            notificationService.deleteNotification(publicId, testUserId);

            // Assert
            assertTrue(notification.isDeleted());
            assertNotNull(notification.getDeletedAt());
            verify(notificationRepository).save(notification);
        }

        @Test
        @DisplayName("Should throw exception when notification not found")
        void deleteNotification_NotFound_ThrowsException() {
            // Arrange
            UUID publicId = UUID.randomUUID();
            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> notificationService.deleteNotification(publicId, testUserId));
        }

        @Test
        @DisplayName("Should throw exception when notification does not belong to user")
        void deleteNotification_WrongUser_ThrowsException() {
            // Arrange
            UUID publicId = UUID.randomUUID();
            Notification notification = new Notification(100L, testPlannerId.toString(), NotificationType.PLANNER_RECOMMENDED);
            notification.setPublicId(publicId);

            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.of(notification));

            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> notificationService.deleteNotification(publicId, 999L));
        }
    }

    @Nested
    @DisplayName("cleanupOldNotifications Tests")
    class CleanupOldNotificationsTests {

        @Test
        @DisplayName("Should soft-delete old read notifications and hard-delete old soft-deleted")
        void cleanupOldNotifications_PerformsCleanup() {
            // Arrange
            when(notificationRepository.softDeleteOldReadNotifications(any(Instant.class), any(Instant.class)))
                    .thenReturn(10);
            when(notificationRepository.hardDeleteOldNotifications(any(Instant.class)))
                    .thenReturn(5);

            // Act
            notificationService.cleanupOldNotifications();

            // Assert
            verify(notificationRepository).softDeleteOldReadNotifications(any(Instant.class), any(Instant.class));
            verify(notificationRepository).hardDeleteOldNotifications(any(Instant.class));
        }

        @Test
        @DisplayName("Should use correct cutoff dates")
        void cleanupOldNotifications_CorrectCutoffDates() {
            // Arrange
            ArgumentCaptor<Instant> softDeleteCutoffCaptor = ArgumentCaptor.forClass(Instant.class);
            ArgumentCaptor<Instant> hardDeleteCutoffCaptor = ArgumentCaptor.forClass(Instant.class);

            when(notificationRepository.softDeleteOldReadNotifications(softDeleteCutoffCaptor.capture(), any(Instant.class)))
                    .thenReturn(0);
            when(notificationRepository.hardDeleteOldNotifications(hardDeleteCutoffCaptor.capture()))
                    .thenReturn(0);

            // Act
            notificationService.cleanupOldNotifications();

            // Assert - verify cutoff dates are approximately correct
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

package org.danteplanner.backend.service;
import org.danteplanner.backend.notification.dto.NotificationEventPayload;
import org.danteplanner.backend.shared.exception.EntityNotFoundException;
import org.danteplanner.backend.notification.event.NotificationRaisedEvent;
import org.springframework.context.ApplicationEventPublisher;

import org.danteplanner.backend.notification.service.NotificationRetentionService;
import org.danteplanner.backend.notification.service.NotificationDispatchService;
import org.danteplanner.backend.notification.service.NotificationInboxService;

import org.danteplanner.backend.notification.dto.NotificationInboxResponse;
import org.danteplanner.backend.notification.dto.NotificationResponse;
import org.danteplanner.backend.notification.dto.UnreadCountResponse;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.entity.NotificationType;
import org.danteplanner.backend.shared.entity.SseEventType;
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

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NotificationInboxService inboxService;
    private NotificationDispatchService dispatchService;
    private NotificationRetentionService retentionService;

    private Long testUserId = 100L;
    private UUID testPlannerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        inboxService = new NotificationInboxService(notificationRepository);
        dispatchService = new NotificationDispatchService(notificationRepository, eventPublisher);
        retentionService = new NotificationRetentionService(notificationRepository);
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
        @DisplayName("Should create PLANNER_RECOMMENDED notification successfully")
        void notifyPlannerRecommended_WhenValid_CreatesNotification() {
            // Arrange - set publicId and createdAt on saved notification (simulating @PrePersist)
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> {
                        Notification n = invocation.getArgument(0);
                        n.setPublicId(UUID.randomUUID());
                        n.setCreatedAt(Instant.now());
                        return n;
                    });

            // Act
            dispatchService.notifyPlannerRecommended(testPlannerId, "Test Planner Title", testUserId);

            // Assert
            // The persisted row is observable only in a containerized test; the captured entity is
            // the nearest stand-in for what reaches the notifications table.
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
        @DisplayName("Should write nothing when the owner was already notified")
        void notifyPlannerRecommended_WhenAlreadyNotified_WritesNothing() {
            // Arrange
            when(notificationRepository.existsByUserIdAndContentIdAndNotificationType(
                    testUserId, testPlannerId.toString(), NotificationType.PLANNER_RECOMMENDED))
                    .thenReturn(true);

            // Act
            dispatchService.notifyPlannerRecommended(testPlannerId, "Test Planner Title", testUserId);

            // Assert - the duplicate is decided before the write, so no violation is ever fired
            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should push saved notification to recipient via SSE on success")
        void notifyPlannerRecommended_WhenSuccess_PushesViaSse() {
            // Arrange
            UUID[] persistedPublicId = new UUID[1];
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> {
                        Notification n = invocation.getArgument(0);
                        n.setPublicId(UUID.randomUUID());
                        n.setCreatedAt(Instant.now());
                        persistedPublicId[0] = n.getPublicId();
                        return n;
                    });

            // Act
            dispatchService.notifyPlannerRecommended(testPlannerId, "Test Planner Title", testUserId);

            // Assert
            ArgumentCaptor<NotificationRaisedEvent> raised =
                    ArgumentCaptor.forClass(NotificationRaisedEvent.class);
            verify(eventPublisher).publishEvent(raised.capture());

            NotificationRaisedEvent event = raised.getValue();
            assertEquals(testUserId, event.userId());
            assertEquals(SseEventType.NOTIFY_RECOMMENDED, event.eventType());
            assertEquals(persistedPublicId[0].toString(), event.entityId());

            NotificationEventPayload payload = event.payload();
            assertEquals(persistedPublicId[0].toString(), payload.id());
            assertEquals(NotificationType.PLANNER_RECOMMENDED.name(), payload.type());
            assertEquals(testPlannerId.toString(), payload.contentId());
            assertEquals(testPlannerId.toString(), payload.plannerId());
            assertEquals("Test Planner Title", payload.plannerTitle());
        }

        @Test
        @DisplayName("Should not push via SSE when the owner was already notified")
        void notifyPlannerRecommended_WhenAlreadyNotified_DoesNotPush() {
            // Arrange
            when(notificationRepository.existsByUserIdAndContentIdAndNotificationType(
                    testUserId, testPlannerId.toString(), NotificationType.PLANNER_RECOMMENDED))
                    .thenReturn(true);

            // Act
            dispatchService.notifyPlannerRecommended(testPlannerId, "Test Planner Title", testUserId);

            // Assert
            verify(eventPublisher, never()).publishEvent(any(NotificationRaisedEvent.class));
        }
    }

    @Nested
    @DisplayName("notifyCommentReceived Tests")
    class NotifyCommentReceivedTests {

        @Test
        @DisplayName("Should create COMMENT_RECEIVED notification when commenter is not owner")
        void notifyCommentReceived_WhenDifferentUser_CreatesNotification() {
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
            dispatchService.notifyCommentReceived(
                    commentId, commentPublicId, testPlannerId, "Test Planner",
                    "Test content", plannerOwnerId, commenterId);

            // Assert
            // The persisted row is observable only in a containerized test; the captured entity is
            // the nearest stand-in for what reaches the notifications table.
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());

            Notification saved = captor.getValue();
            assertEquals(plannerOwnerId, saved.getUserId());
            assertEquals(commentId.toString(), saved.getContentId());
            assertEquals(NotificationType.COMMENT_RECEIVED, saved.getNotificationType());
        }

        @Test
        @DisplayName("Should not notify when commenter is the planner owner")
        void notifyCommentReceived_WhenSameUser_NoNotification() {
            // Arrange
            Long userId = 100L;
            Long commentId = 999L;
            UUID commentPublicId = UUID.randomUUID();

            // Act
            dispatchService.notifyCommentReceived(
                    commentId, commentPublicId, testPlannerId, "Test Planner",
                    "Test content", userId, userId);

            // Assert
            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should write nothing when the owner was already notified")
        void notifyCommentReceived_WhenAlreadyNotified_WritesNothing() {
            // Arrange
            Long plannerOwnerId = 100L;
            Long commenterId = 200L;
            Long commentId = 999L;
            UUID commentPublicId = UUID.randomUUID();

            // A comment keys on its own fresh primary key, so production never reaches this;
            // asserted because the entry point joins its caller's transaction, where a fired
            // constraint would poison the caller rather than suppress anything.
            when(notificationRepository.existsByUserIdAndContentIdAndNotificationType(
                    plannerOwnerId, commentId.toString(), NotificationType.COMMENT_RECEIVED))
                    .thenReturn(true);

            // Act
            dispatchService.notifyCommentReceived(
                    commentId, commentPublicId, testPlannerId, "Test Planner",
                    "Test content", plannerOwnerId, commenterId);

            // Assert
            verify(notificationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("notifyReplyReceived Tests")
    class NotifyReplyReceivedTests {

        @Test
        @DisplayName("Should create REPLY_RECEIVED notification when replier is not parent author")
        void notifyReplyReceived_WhenDifferentUser_CreatesNotification() {
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
            dispatchService.notifyReplyReceived(
                    replyId, replyPublicId, testPlannerId, "Test Planner",
                    "Reply content", parentAuthorId, replierId);

            // Assert
            // The persisted row is observable only in a containerized test; the captured entity is
            // the nearest stand-in for what reaches the notifications table.
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());

            Notification saved = captor.getValue();
            assertEquals(parentAuthorId, saved.getUserId());
            assertEquals(replyId.toString(), saved.getContentId());
            assertEquals(NotificationType.REPLY_RECEIVED, saved.getNotificationType());
        }

        @Test
        @DisplayName("Should not notify when replier is the parent comment author")
        void notifyReplyReceived_WhenSameUser_NoNotification() {
            // Arrange
            Long userId = 100L;
            Long replyId = 101L;
            UUID replyPublicId = UUID.randomUUID();

            // Act
            dispatchService.notifyReplyReceived(
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
            assertFalse(notification.getRead());

            when(notificationRepository.findByPublicId(publicId))
                    .thenReturn(Optional.of(notification));
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            NotificationResponse response = inboxService.markAsRead(publicId, testUserId);

            // Assert
            assertTrue(notification.getRead());
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
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

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

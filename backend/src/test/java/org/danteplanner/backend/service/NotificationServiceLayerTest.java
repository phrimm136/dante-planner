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
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

    private static final Instant NOW = Instant.parse("2026-11-26T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationInboxService inboxService;
    private NotificationDispatchService dispatchService;
    private NotificationRetentionService retentionService;

    private Long testUserId = 100L;
    private UUID testPlannerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        inboxService = new NotificationInboxService(notificationRepository, CLOCK);
        dispatchService = new NotificationDispatchService(notificationRepository);
        retentionService = new NotificationRetentionService(notificationRepository, CLOCK);
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
        Notification stored = new Notification(userId, contentId, type,
                new Notification.Content.PlannerScoped(testPlannerId, plannerTitle));
        UUID publicId = UUID.randomUUID();
        ReflectionTestUtils.setField(stored, "publicId", publicId);
        ReflectionTestUtils.setField(stored, "createdAt", Instant.now());
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

            when(notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(eq(testUserId), any(Pageable.class)))
                    .thenReturn(page);

            // Act
            NotificationInboxResponse response = inboxService.getInbox(testUserId, 0, 20).value();

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

            when(notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(eq(testUserId), any(Pageable.class)))
                    .thenReturn(emptyPage);

            // Act
            NotificationInboxResponse response = inboxService.getInbox(testUserId, 0, 20).value();

            // Assert
            assertThat(response.notifications()).isEmpty();
            assertThat(response.totalElements()).isEqualTo(0);
        }

        @Test
        void getInbox_WhenRead_ScopesTheRecipientAndGlobal() {
            when(notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(eq(7L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            assertThat(inboxService.getInbox(7L, 0, 20).scopes())
                    .containsExactlyInAnyOrder(new FreshnessScope.User(7L), new FreshnessScope.Global());
        }
    }

    @Nested
    class GetUnreadCountTests {

        @Test
        void getUnreadCount_WhenUnreadExist_ReturnsCount() {
            // Arrange
            when(notificationRepository.countByUserIdAndReadAtIsNull(testUserId))
                    .thenReturn(5L);

            // Act
            UnreadCountResponse response = inboxService.getUnreadCount(testUserId).value();

            // Assert
            assertThat(response.unreadCount()).isEqualTo(5L);
        }

        @Test
        void getUnreadCount_WhenNoUnread_ReturnsZero() {
            // Arrange
            when(notificationRepository.countByUserIdAndReadAtIsNull(testUserId))
                    .thenReturn(0L);

            // Act
            UnreadCountResponse response = inboxService.getUnreadCount(testUserId).value();

            // Assert
            assertThat(response.unreadCount()).isEqualTo(0L);
        }

        @Test
        void getUnreadCount_WhenRead_ScopesTheRecipientAndGlobal() {
            when(notificationRepository.countByUserIdAndReadAtIsNull(7L)).thenReturn(0L);

            assertThat(inboxService.getUnreadCount(7L).scopes())
                    .containsExactlyInAnyOrder(new FreshnessScope.User(7L), new FreshnessScope.Global());
        }
    }

    @Nested
    class MarkAsReadTests {

        @Test
        void markAsRead_WhenTheOwnRowExists_MarksBeforeReadingAndAnswersTheStoredReadAt() {
            UUID publicId = UUID.randomUUID();
            Instant firstReadAt = NOW.minusSeconds(60);
            Notification notification = new Notification(
                    testUserId, testPlannerId.toString(), NotificationType.PLANNER_RECOMMENDED);
            ReflectionTestUtils.setField(notification, "publicId", publicId);
            ReflectionTestUtils.setField(notification, "readAt", firstReadAt);
            when(notificationRepository.findByPublicIdAndUserId(publicId, testUserId))
                    .thenReturn(Optional.of(notification));

            NotificationResponse response = inboxService.markAsRead(publicId, testUserId);

            assertThat(response.id()).isEqualTo(publicId);
            assertThat(response.read()).isTrue();
            assertThat(response.readAt()).isEqualTo(firstReadAt);
            InOrder order = inOrder(notificationRepository);
            order.verify(notificationRepository).markAsRead(publicId, testUserId, NOW);
            order.verify(notificationRepository).findByPublicIdAndUserId(publicId, testUserId);
        }

        @Test
        void markAsRead_WhenNoOwnRow_ThrowsException() {
            UUID publicId = UUID.randomUUID();
            when(notificationRepository.findByPublicIdAndUserId(publicId, testUserId))
                    .thenReturn(Optional.empty());

            assertRejected(() -> inboxService.markAsRead(publicId, testUserId))
                    .withCode("NOT_FOUND")
                    .satisfies(rejection -> assertThat(rejection.getMessage())
                            .isEqualTo("Notification not found with id: " + publicId));
        }
    }

    @Nested
    class MarkAllAsReadTests {

        @Test
        void markAllAsRead_WhenUnreadExist_MarksTheirIdsAndAnswersTheMatchedCount() {
            when(notificationRepository.unreadIdsOf(testUserId)).thenReturn(List.of(3L, 5L, 8L));
            when(notificationRepository.markAllAsRead(List.of(3L, 5L, 8L), NOW)).thenReturn(2);

            assertThat(inboxService.markAllAsRead(testUserId)).isEqualTo(2);
        }
    }

    @Nested
    class CleanupOldNotificationsTests {

        @Test
        void cleanupOldNotifications_WhenRun_DeletesWhatWasReadOver365DaysAgo() {
            retentionService.purgeExpired();

            verify(notificationRepository).deleteReadBefore(NOW.minus(365, ChronoUnit.DAYS));
            verifyNoMoreInteractions(notificationRepository);
        }
    }
}

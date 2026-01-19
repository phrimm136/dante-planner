package org.danteplanner.backend.controller;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.entity.Notification;
import org.danteplanner.backend.entity.NotificationType;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.repository.NotificationRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.service.token.JwtTokenService;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
@Transactional
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private User testUser;
    private User otherUser;
    private String accessToken;
    private String otherUserAccessToken;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);

        testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");
        otherUser = TestDataFactory.createTestUser(userRepository, "other@example.com");
        accessToken = TestDataFactory.generateAccessToken(jwtTokenService, testUser);
        otherUserAccessToken = TestDataFactory.generateAccessToken(jwtTokenService, otherUser);
    }

    private Cookie accessTokenCookie() {
        return new Cookie("accessToken", accessToken);
    }

    private Cookie otherUserAccessTokenCookie() {
        return new Cookie("accessToken", otherUserAccessToken);
    }

    private Notification createNotification(User recipient, NotificationType type) {
        Notification notification = new Notification(
                recipient.getId(),
                "test-content-" + System.nanoTime(),
                type
        );
        return notificationRepository.save(notification);
    }

    /**
     * Sets the createdAt timestamp for a notification.
     * Uses entity setter for consistency with PlannerComment, PlannerView patterns.
     */
    private void setCreatedAt(Notification notification, Instant timestamp) {
        notification.setCreatedAt(timestamp);
        notificationRepository.save(notification);
    }

    @Nested
    @DisplayName("GET /api/notifications/inbox - Get Inbox")
    class GetInboxTests {

        @Test
        @DisplayName("Should return paginated response structure with notifications")
        void getInbox_WithNotifications_ReturnsPaginatedStructure() throws Exception {
            for (int i = 0; i < 25; i++) {
                createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            }

            mockMvc.perform(get("/api/notifications/inbox")
                            .cookie(accessTokenCookie())
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notifications").isArray())
                    .andExpect(jsonPath("$.notifications.length()").value(10))
                    .andExpect(jsonPath("$.totalElements").value(25))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(10));
        }

        @Test
        @DisplayName("Should return empty page when inbox is empty")
        void getInbox_EmptyInbox_ReturnsEmptyPage() throws Exception {
            mockMvc.perform(get("/api/notifications/inbox")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notifications").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.totalPages").value(0));
        }

        @Test
        @DisplayName("Should respect custom page size")
        void getInbox_CustomPageSize_RespectsSize() throws Exception {
            for (int i = 0; i < 60; i++) {
                createNotification(testUser, NotificationType.REPLY_RECEIVED);
            }

            mockMvc.perform(get("/api/notifications/inbox")
                            .cookie(accessTokenCookie())
                            .param("size", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notifications.length()").value(50))
                    .andExpect(jsonPath("$.size").value(50));
        }

        @Test
        @DisplayName("Should enforce max page size of 100")
        void getInbox_MaxSizeEnforcement_ClampsTo100() throws Exception {
            for (int i = 0; i < 150; i++) {
                createNotification(testUser, NotificationType.PLANNER_RECOMMENDED);
            }

            mockMvc.perform(get("/api/notifications/inbox")
                            .cookie(accessTokenCookie())
                            .param("size", "200"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notifications.length()").value(100))
                    .andExpect(jsonPath("$.size").value(100));
        }

        @Test
        @DisplayName("Should use default size of 20 when not specified")
        void getInbox_DefaultSize_Uses20() throws Exception {
            for (int i = 0; i < 30; i++) {
                createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            }

            mockMvc.perform(get("/api/notifications/inbox")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notifications.length()").value(20))
                    .andExpect(jsonPath("$.size").value(20));
        }

        @Test
        @DisplayName("Should order notifications DESC by createdAt (newest first)")
        void getInbox_Ordering_NewestFirst() throws Exception {
            Instant now = Instant.now();
            Notification old = createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            setCreatedAt(old, now.minusSeconds(10));
            Notification newer = createNotification(testUser, NotificationType.REPLY_RECEIVED);
            setCreatedAt(newer, now);

            mockMvc.perform(get("/api/notifications/inbox")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notifications[0].id").value(newer.getPublicId().toString()))
                    .andExpect(jsonPath("$.notifications[1].id").value(old.getPublicId().toString()));
        }

        @Test
        @DisplayName("Should exclude soft-deleted notifications from inbox")
        void getInbox_SoftDeleted_ExcludesFromInbox() throws Exception {
            Notification active = createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            Notification deleted = createNotification(testUser, NotificationType.REPLY_RECEIVED);
            deleted.softDelete();
            notificationRepository.save(deleted);

            mockMvc.perform(get("/api/notifications/inbox")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notifications.length()").value(1))
                    .andExpect(jsonPath("$.notifications[0].id").value(active.getPublicId().toString()));
        }

        @Test
        @DisplayName("Should only return notifications for authenticated user")
        void getInbox_UserIsolation_OnlyOwnNotifications() throws Exception {
            createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            createNotification(testUser, NotificationType.REPLY_RECEIVED);
            createNotification(otherUser, NotificationType.PLANNER_RECOMMENDED);

            mockMvc.perform(get("/api/notifications/inbox")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @DisplayName("Should return 401 when unauthenticated")
        void getInbox_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/notifications/inbox"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/notifications/unread-count - Get Unread Count")
    class GetUnreadCountTests {

        @Test
        @DisplayName("Should return count of unread notifications")
        void getUnreadCount_WithUnread_ReturnsCorrectCount() throws Exception {
            createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            createNotification(testUser, NotificationType.REPLY_RECEIVED);
            createNotification(testUser, NotificationType.PLANNER_RECOMMENDED);

            mockMvc.perform(get("/api/notifications/unread-count")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount").value(3));
        }

        @Test
        @DisplayName("Should return zero when no notifications exist")
        void getUnreadCount_NoNotifications_ReturnsZero() throws Exception {
            mockMvc.perform(get("/api/notifications/unread-count")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount").value(0));
        }

        @Test
        @DisplayName("Should exclude read notifications from count")
        void getUnreadCount_MixedReadStatus_ReturnsUnreadOnly() throws Exception {
            createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            Notification read = createNotification(testUser, NotificationType.REPLY_RECEIVED);
            read.markAsRead();
            notificationRepository.save(read);

            mockMvc.perform(get("/api/notifications/unread-count")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount").value(1));
        }

        @Test
        @DisplayName("Should exclude soft-deleted notifications from count")
        void getUnreadCount_SoftDeleted_ExcludesFromCount() throws Exception {
            createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            Notification deleted = createNotification(testUser, NotificationType.REPLY_RECEIVED);
            deleted.softDelete();
            notificationRepository.save(deleted);

            mockMvc.perform(get("/api/notifications/unread-count")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount").value(1));
        }

        @Test
        @DisplayName("Should only count authenticated user's notifications")
        void getUnreadCount_UserIsolation_OnlyCountsOwn() throws Exception {
            createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            createNotification(otherUser, NotificationType.REPLY_RECEIVED);
            createNotification(otherUser, NotificationType.PLANNER_RECOMMENDED);

            mockMvc.perform(get("/api/notifications/unread-count")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount").value(1));
        }

        @Test
        @DisplayName("Should return 401 when unauthenticated")
        void getUnreadCount_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/notifications/unread-count"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/notifications/{id}/mark-read - Mark Single Read")
    class MarkAsReadTests {

        @Test
        @DisplayName("Should mark unread notification as read and return 200")
        void markRead_UnreadNotification_Returns200() throws Exception {
            Notification notification = createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            assertThat(notification.getRead()).isFalse();

            mockMvc.perform(post("/api/notifications/{id}/mark-read", notification.getPublicId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(notification.getPublicId().toString()))
                    .andExpect(jsonPath("$.read").value(true));
        }

        @Test
        @DisplayName("Should set readAt timestamp when marking as read")
        void markRead_UnreadNotification_SetsReadAt() throws Exception {
            Notification notification = createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            assertThat(notification.getReadAt()).isNull();

            mockMvc.perform(post("/api/notifications/{id}/mark-read", notification.getPublicId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk());

            Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
            assertThat(updated.getReadAt()).isNotNull();
            assertThat(updated.getRead()).isTrue();
        }

        @Test
        @DisplayName("Should be idempotent when marking already read notification")
        void markRead_AlreadyRead_IsIdempotent() throws Exception {
            Notification notification = createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            notification.markAsRead();
            notificationRepository.save(notification);
            Instant firstReadAt = notification.getReadAt();

            mockMvc.perform(post("/api/notifications/{id}/mark-read", notification.getPublicId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.read").value(true));

            Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
            assertThat(updated.getReadAt()).isEqualTo(firstReadAt);
        }

        @Test
        @DisplayName("Should return 403 when user is not notification owner")
        void markRead_NotOwner_Returns403() throws Exception {
            Notification otherUserNotification = createNotification(otherUser, NotificationType.COMMENT_RECEIVED);

            mockMvc.perform(post("/api/notifications/{id}/mark-read", otherUserNotification.getPublicId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 403 when notification does not exist")
        void markRead_NonexistentId_Returns403() throws Exception {
            mockMvc.perform(post("/api/notifications/{id}/mark-read", UUID.randomUUID())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 401 when unauthenticated")
        void markRead_Unauthenticated_Returns401() throws Exception {
            Notification notification = createNotification(testUser, NotificationType.COMMENT_RECEIVED);

            mockMvc.perform(post("/api/notifications/{id}/mark-read", notification.getPublicId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/notifications/mark-all-read - Mark All Read")
    class MarkAllAsReadTests {

        @Test
        @DisplayName("Should mark all unread notifications as read")
        void markAllRead_MultipleUnread_MarksAll() throws Exception {
            createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            createNotification(testUser, NotificationType.REPLY_RECEIVED);
            createNotification(testUser, NotificationType.PLANNER_RECOMMENDED);

            mockMvc.perform(post("/api/notifications/mark-all-read")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(3));

            long unreadCount = notificationRepository.countByUserIdAndReadFalseAndDeletedAtIsNull(testUser.getId());
            assertThat(unreadCount).isZero();
        }

        @Test
        @DisplayName("Should return count of marked notifications")
        void markAllRead_ReturnsCount() throws Exception {
            createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            createNotification(testUser, NotificationType.REPLY_RECEIVED);

            mockMvc.perform(post("/api/notifications/mark-all-read")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(2));
        }

        @Test
        @DisplayName("Should not affect already read notifications")
        void markAllRead_AlreadyRead_Unchanged() throws Exception {
            Notification read = createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            read.markAsRead();
            notificationRepository.save(read);
            Instant firstReadAt = read.getReadAt();

            createNotification(testUser, NotificationType.REPLY_RECEIVED);

            mockMvc.perform(post("/api/notifications/mark-all-read")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(1));

            Notification unchanged = notificationRepository.findById(read.getId()).orElseThrow();
            assertThat(unchanged.getReadAt()).isEqualTo(firstReadAt);
        }

        @Test
        @DisplayName("Should only mark authenticated user's notifications")
        void markAllRead_UserIsolation_OnlyMarksOwn() throws Exception {
            createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            createNotification(otherUser, NotificationType.REPLY_RECEIVED);

            mockMvc.perform(post("/api/notifications/mark-all-read")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(1));

            long otherUserUnreadCount = notificationRepository.countByUserIdAndReadFalseAndDeletedAtIsNull(otherUser.getId());
            assertThat(otherUserUnreadCount).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return 0 when no unread notifications exist")
        void markAllRead_NoUnread_ReturnsZero() throws Exception {
            mockMvc.perform(post("/api/notifications/mark-all-read")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(0));
        }

        @Test
        @DisplayName("Should return 401 when unauthenticated")
        void markAllRead_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(post("/api/notifications/mark-all-read"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /api/notifications/{id} - Delete Notification")
    class DeleteNotificationTests {

        @Test
        @DisplayName("Should soft-delete notification and return 204")
        void deleteNotification_ValidRequest_Returns204() throws Exception {
            Notification notification = createNotification(testUser, NotificationType.COMMENT_RECEIVED);

            mockMvc.perform(delete("/api/notifications/{id}", notification.getPublicId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNoContent());

            Notification deleted = notificationRepository.findById(notification.getId()).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();
            assertThat(deleted.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("Should return 403 when user is not notification owner")
        void deleteNotification_NotOwner_Returns403() throws Exception {
            Notification otherUserNotification = createNotification(otherUser, NotificationType.COMMENT_RECEIVED);

            mockMvc.perform(delete("/api/notifications/{id}", otherUserNotification.getPublicId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should exclude deleted notification from inbox")
        void deleteNotification_ExcludesFromInbox() throws Exception {
            Notification notification = createNotification(testUser, NotificationType.COMMENT_RECEIVED);

            mockMvc.perform(delete("/api/notifications/{id}", notification.getPublicId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/notifications/inbox")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notifications").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("Should exclude deleted notification from unread count")
        void deleteNotification_ExcludesFromUnreadCount() throws Exception {
            Notification notification = createNotification(testUser, NotificationType.COMMENT_RECEIVED);

            mockMvc.perform(delete("/api/notifications/{id}", notification.getPublicId())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/notifications/unread-count")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount").value(0));
        }

        @Test
        @DisplayName("Should return 403 when notification does not exist")
        void deleteNotification_NonexistentId_Returns403() throws Exception {
            mockMvc.perform(delete("/api/notifications/{id}", UUID.randomUUID())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 401 when unauthenticated")
        void deleteNotification_Unauthenticated_Returns401() throws Exception {
            Notification notification = createNotification(testUser, NotificationType.COMMENT_RECEIVED);

            mockMvc.perform(delete("/api/notifications/{id}", notification.getPublicId()))
                    .andExpect(status().isUnauthorized());
        }
    }
}

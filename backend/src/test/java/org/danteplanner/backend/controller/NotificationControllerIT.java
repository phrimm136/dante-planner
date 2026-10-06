package org.danteplanner.backend.controller;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import java.time.temporal.ChronoUnit;
import static org.assertj.core.api.Assertions.within;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.api.NotificationType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class NotificationControllerIT extends SharedMySqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private User testUser;
    private User otherUser;
    private String accessToken;
    private String otherUserAccessToken;

    @BeforeEach
    void setUp() {

        testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");
        otherUser = TestDataFactory.createTestUser(userRepository, "other@example.com");
        accessToken = TestDataFactory.generateAccessToken(jwtTokenService, testUser);
        otherUserAccessToken = TestDataFactory.generateAccessToken(jwtTokenService, otherUser);
    }

    private Cookie accessTokenCookie() {
        return AuthCookies.accessToken(accessToken);
    }

    private Cookie otherUserAccessTokenCookie() {
        return AuthCookies.accessToken(otherUserAccessToken);
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
    class GetInboxTests {

        @Test
        void getInbox_WhenNotifications_ReturnsPaginatedStructure() throws Exception {
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
        void getInbox_WhenEmptyInbox_ReturnsEmptyPage() throws Exception {
            mockMvc.perform(get("/api/notifications/inbox")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notifications").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.totalPages").value(0));
        }

        @Test
        void getInbox_WhenCustomPageSize_RespectsSize() throws Exception {
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
        void getInbox_WhenMaxSizeEnforcement_ClampsTo100() throws Exception {
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
        void getInbox_WhenDefaultSize_Uses20() throws Exception {
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
        void getInbox_WhenOrdering_NewestFirst() throws Exception {
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
        void getInbox_WhenSoftDeleted_ExcludesFromInbox() throws Exception {
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
        void getInbox_WhenUserIsolation_OnlyOwnNotifications() throws Exception {
            createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            createNotification(testUser, NotificationType.REPLY_RECEIVED);
            createNotification(otherUser, NotificationType.PLANNER_RECOMMENDED);

            mockMvc.perform(get("/api/notifications/inbox")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void getInbox_WhenUnauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/notifications/inbox"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class GetUnreadCountTests {

        @Test
        void getUnreadCount_WhenUnread_ReturnsCorrectCount() throws Exception {
            createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            createNotification(testUser, NotificationType.REPLY_RECEIVED);
            createNotification(testUser, NotificationType.PLANNER_RECOMMENDED);

            mockMvc.perform(get("/api/notifications/unread-count")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount").value(3));
        }

        @Test
        void getUnreadCount_WhenNoNotifications_ReturnsZero() throws Exception {
            mockMvc.perform(get("/api/notifications/unread-count")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount").value(0));
        }

        @Test
        void getUnreadCount_WhenMixedReadStatus_ReturnsUnreadOnly() throws Exception {
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
        void getUnreadCount_WhenSoftDeleted_ExcludesFromCount() throws Exception {
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
        void getUnreadCount_WhenUserIsolation_OnlyCountsOwn() throws Exception {
            createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            createNotification(otherUser, NotificationType.REPLY_RECEIVED);
            createNotification(otherUser, NotificationType.PLANNER_RECOMMENDED);

            mockMvc.perform(get("/api/notifications/unread-count")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount").value(1));
        }

        @Test
        void getUnreadCount_WhenUnauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/notifications/unread-count"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class MarkAsReadTests {

        @Test
        void markRead_WhenUnreadNotification_Returns200() throws Exception {
            Notification notification = createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            assertThat(notification.isRead()).isFalse();

            mockMvc.perform(post("/api/notifications/{id}/mark-read", notification.getPublicId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(notification.getPublicId().toString()))
                    .andExpect(jsonPath("$.read").value(true));
        }

        @Test
        void markRead_WhenUnreadNotification_SetsReadAt() throws Exception {
            Notification notification = createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            assertThat(notification.getReadAt()).isNull();

            mockMvc.perform(post("/api/notifications/{id}/mark-read", notification.getPublicId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk());

            Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
            assertThat(updated.getReadAt()).isNotNull();
            assertThat(updated.isRead()).isTrue();
        }

        @Test
        void markRead_WhenAlreadyRead_IsIdempotent() throws Exception {
            Notification notification = createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            notification.markAsRead();
            notificationRepository.save(notification);
            Instant firstReadAt = notification.getReadAt();

            mockMvc.perform(post("/api/notifications/{id}/mark-read", notification.getPublicId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.read").value(true));

            Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
            // MySQL DATETIME(6) truncates the nanoseconds an in-memory Instant carries; with no
            // test transaction the value round-trips instead of coming back from the session.
            assertThat(updated.getReadAt()).isCloseTo(firstReadAt, within(1, ChronoUnit.MILLIS));
        }

        @Test
        void markRead_WhenNotOwner_Returns404() throws Exception {
            Notification otherUserNotification = createNotification(otherUser, NotificationType.COMMENT_RECEIVED);

            mockMvc.perform(post("/api/notifications/{id}/mark-read", otherUserNotification.getPublicId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void markRead_WhenNonexistentId_Returns404() throws Exception {
            mockMvc.perform(post("/api/notifications/{id}/mark-read", UUID.randomUUID()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void markRead_WhenUnauthenticated_Returns401() throws Exception {
            Notification notification = createNotification(testUser, NotificationType.COMMENT_RECEIVED);

            mockMvc.perform(post("/api/notifications/{id}/mark-read", notification.getPublicId()).with(withCsrf()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class MarkAllAsReadTests {

        @Test
        void markAllRead_WhenMultipleUnread_MarksAll() throws Exception {
            createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            createNotification(testUser, NotificationType.REPLY_RECEIVED);
            createNotification(testUser, NotificationType.PLANNER_RECOMMENDED);

            mockMvc.perform(post("/api/notifications/mark-all-read").with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.affected").value(3));

            long unreadCount = notificationRepository.countByUserIdAndReadFalseAndDeletedAtIsNull(testUser.getId());
            assertThat(unreadCount).isZero();
        }

        @Test
        void markAllRead_WhenUnreadExist_ReturnsCount() throws Exception {
            createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            createNotification(testUser, NotificationType.REPLY_RECEIVED);

            mockMvc.perform(post("/api/notifications/mark-all-read").with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.affected").value(2));
        }

        @Test
        void markAllRead_WhenAlreadyRead_Unchanged() throws Exception {
            Notification read = createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            read.markAsRead();
            notificationRepository.save(read);
            Instant firstReadAt = read.getReadAt();

            createNotification(testUser, NotificationType.REPLY_RECEIVED);

            mockMvc.perform(post("/api/notifications/mark-all-read").with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.affected").value(1));

            Notification unchanged = notificationRepository.findById(read.getId()).orElseThrow();
            // Compared at millisecond precision: with no test transaction the entity is
            // re-read from MySQL, whose DATETIME(6) does not carry the nanoseconds an
            // in-memory Instant holds.
            assertThat(unchanged.getReadAt()).isCloseTo(firstReadAt, within(1, ChronoUnit.MILLIS));
        }

        @Test
        void markAllRead_WhenUserIsolation_OnlyMarksOwn() throws Exception {
            createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            createNotification(otherUser, NotificationType.REPLY_RECEIVED);

            mockMvc.perform(post("/api/notifications/mark-all-read").with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.affected").value(1));

            long otherUserUnreadCount = notificationRepository.countByUserIdAndReadFalseAndDeletedAtIsNull(otherUser.getId());
            assertThat(otherUserUnreadCount).isEqualTo(1);
        }

        @Test
        void markAllRead_WhenNoUnread_ReturnsZero() throws Exception {
            mockMvc.perform(post("/api/notifications/mark-all-read").with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.affected").value(0));
        }

        @Test
        void markAllRead_WhenUnauthenticated_Returns401() throws Exception {
            mockMvc.perform(post("/api/notifications/mark-all-read").with(withCsrf()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class DeleteNotificationTests {

        @Test
        void deleteNotification_WhenValidRequest_Returns204() throws Exception {
            Notification notification = createNotification(testUser, NotificationType.COMMENT_RECEIVED);

            mockMvc.perform(delete("/api/notifications/{id}", notification.getPublicId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNoContent());

            Notification deleted = notificationRepository.findById(notification.getId()).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();
            assertThat(deleted.isDeleted()).isTrue();
        }

        @Test
        void deleteNotification_WhenNotOwner_Returns404() throws Exception {
            Notification otherUserNotification = createNotification(otherUser, NotificationType.COMMENT_RECEIVED);

            mockMvc.perform(delete("/api/notifications/{id}", otherUserNotification.getPublicId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void deleteNotification_WhenDeleted_ExcludesFromInbox() throws Exception {
            Notification notification = createNotification(testUser, NotificationType.COMMENT_RECEIVED);

            mockMvc.perform(delete("/api/notifications/{id}", notification.getPublicId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/notifications/inbox")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notifications").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void deleteNotification_WhenDeleted_ExcludesFromUnreadCount() throws Exception {
            Notification notification = createNotification(testUser, NotificationType.COMMENT_RECEIVED);

            mockMvc.perform(delete("/api/notifications/{id}", notification.getPublicId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/notifications/unread-count")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount").value(0));
        }

        @Test
        void deleteNotification_WhenNonexistentId_Returns404() throws Exception {
            mockMvc.perform(delete("/api/notifications/{id}", UUID.randomUUID()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void deleteNotification_WhenUnauthenticated_Returns401() throws Exception {
            Notification notification = createNotification(testUser, NotificationType.COMMENT_RECEIVED);

            mockMvc.perform(delete("/api/notifications/{id}", notification.getPublicId()).with(withCsrf()))
                    .andExpect(status().isUnauthorized());
        }
    }
}

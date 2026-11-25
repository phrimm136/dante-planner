package org.danteplanner.backend.controller;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.api.NotificationType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
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
    private JwtTokenService jwtTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User testUser;
    private User otherUser;
    private String accessToken;

    @BeforeEach
    void setUp() {

        testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");
        otherUser = TestDataFactory.createTestUser(userRepository, "other@example.com");
        accessToken = TestDataFactory.generateAccessToken(jwtTokenService, testUser);
    }

    private Cookie accessTokenCookie() {
        return AuthCookies.accessToken(accessToken);
    }

    private Notification createNotification(User recipient, NotificationType type) {
        Notification notification = new Notification(
                recipient.getId(),
                "test-content-" + System.nanoTime(),
                type
        );
        return notificationRepository.save(notification);
    }

    private Notification createRead(User recipient, NotificationType type) {
        Notification notification = createNotification(recipient, type);
        jdbcTemplate.update("UPDATE notifications SET read_at = '2026-01-02 03:04:05.678901' WHERE id = ?",
                notification.getId());
        return stored(notification);
    }

    private Notification stored(Notification notification) {
        return notificationRepository.findById(notification.getId()).orElseThrow();
    }

    private void assertLeftTheInbox(Notification... marked) throws Exception {
        for (Notification notification : marked) {
            assertThat(stored(notification).getReadAt()).isNotNull();
        }
        mockMvc.perform(get("/api/notifications/inbox")
                        .cookie(accessTokenCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/notifications/unread-count")
                        .cookie(accessTokenCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    private void setCreatedAt(Notification notification, Instant timestamp) {
        jdbcTemplate.update("UPDATE notifications SET created_at = ? WHERE id = ?",
                Timestamp.from(timestamp), notification.getId());
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

        @ParameterizedTest
        @CsvSource({"page, -1", "size, 0", "size, -1", "page, x"})
        void getInbox_WhenPageOrSizeIsNotInRange_Returns400(String parameter, String value) throws Exception {
            mockMvc.perform(get("/api/notifications/inbox")
                            .cookie(accessTokenCookie())
                            .param(parameter, value))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.detail").value("Invalid parameter format"));
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
            Notification newer = createNotification(testUser, NotificationType.REPLY_RECEIVED);
            Notification old = createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            setCreatedAt(newer, now);
            setCreatedAt(old, now.minusSeconds(10));

            mockMvc.perform(get("/api/notifications/inbox")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notifications[0].id").value(newer.getPublicId().toString()))
                    .andExpect(jsonPath("$.notifications[1].id").value(old.getPublicId().toString()));
        }

        @Test
        void getInbox_WhenRead_ExcludesFromInbox() throws Exception {
            Notification active = createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            createRead(testUser, NotificationType.REPLY_RECEIVED);

            mockMvc.perform(get("/api/notifications/inbox")
                            .cookie(accessTokenCookie())
                            .param("page", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notifications").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(1));
            mockMvc.perform(get("/api/notifications/inbox")
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notifications.length()").value(1))
                    .andExpect(jsonPath("$.notifications[0].read").value(false))
                    .andExpect(jsonPath("$.notifications[0].readAt").doesNotExist())
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
            createRead(testUser, NotificationType.REPLY_RECEIVED);

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
            assertThat(notification.getReadAt()).isNull();

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
        }

        @Test
        void markRead_WhenRepeated_AnswersTheFirstReadAt() throws Exception {
            Notification notification = createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            mockMvc.perform(post("/api/notifications/{id}/mark-read", notification.getPublicId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk());
            jdbcTemplate.update("UPDATE notifications SET read_at = DATE_SUB(read_at, INTERVAL 1 DAY) WHERE id = ?",
                    notification.getId());
            Instant firstReadAt = stored(notification).getReadAt();

            mockMvc.perform(post("/api/notifications/{id}/mark-read", notification.getPublicId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.read").value(true))
                    .andExpect(jsonPath("$.readAt").value(firstReadAt.toString()));

            assertThat(stored(notification).getReadAt()).isEqualTo(firstReadAt);
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

            long unreadCount = notificationRepository.countByUserIdAndReadAtIsNull(testUser.getId());
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
            Notification read = createRead(testUser, NotificationType.COMMENT_RECEIVED);
            createNotification(testUser, NotificationType.REPLY_RECEIVED);

            mockMvc.perform(post("/api/notifications/mark-all-read").with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.affected").value(1));

            assertThat(stored(read).getReadAt()).isEqualTo(read.getReadAt());
        }

        @Test
        void markAllRead_WhenUserIsolation_OnlyMarksOwn() throws Exception {
            createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            createNotification(otherUser, NotificationType.REPLY_RECEIVED);

            mockMvc.perform(post("/api/notifications/mark-all-read").with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.affected").value(1));

            long otherUserUnreadCount = notificationRepository.countByUserIdAndReadAtIsNull(otherUser.getId());
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

            assertLeftTheInbox(notification);
        }

        @Test
        void deleteNotification_WhenRepeated_Returns204AndKeepsTheFirstReadAt() throws Exception {
            Notification read = createRead(testUser, NotificationType.COMMENT_RECEIVED);

            for (int repeat = 0; repeat < 2; repeat++) {
                mockMvc.perform(delete("/api/notifications/{id}", read.getPublicId()).with(withCsrf())
                                .cookie(accessTokenCookie()))
                        .andExpect(status().isNoContent());
            }

            assertThat(stored(read).getReadAt()).isEqualTo(read.getReadAt());
        }

        @Test
        void deleteNotification_WhenNotOwner_Returns404AndLeavesItUnread() throws Exception {
            Notification otherUserNotification = createNotification(otherUser, NotificationType.COMMENT_RECEIVED);

            mockMvc.perform(delete("/api/notifications/{id}", otherUserNotification.getPublicId()).with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isNotFound());

            assertThat(stored(otherUserNotification).getReadAt()).isNull();
        }

        @Test
        void deleteAll_WhenSomeAreReadOrForeign_MarksOwnUnreadAndAnswersTheirCount() throws Exception {
            Notification first = createNotification(testUser, NotificationType.COMMENT_RECEIVED);
            Notification second = createNotification(testUser, NotificationType.REPLY_RECEIVED);
            Notification read = createRead(testUser, NotificationType.COMMENT_RECEIVED);
            Notification foreign = createNotification(otherUser, NotificationType.COMMENT_RECEIVED);

            mockMvc.perform(delete("/api/notifications/all").with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.affected").value(2));

            assertLeftTheInbox(first, second);
            assertThat(stored(read).getReadAt()).isEqualTo(read.getReadAt());
            assertThat(stored(foreign).getReadAt()).isNull();
            mockMvc.perform(delete("/api/notifications/all").with(withCsrf())
                            .cookie(accessTokenCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.affected").value(0));
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

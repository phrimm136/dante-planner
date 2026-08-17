package org.danteplanner.backend.integration;

import java.util.UUID;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.entity.NotificationType;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.repository.UserSettingsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the single-statement PLANNER_PUBLISHED fan-out against real MySQL: one
 * {@code INSERT IGNORE ... SELECT FROM user_settings} inserts a row for each enabled, non-author,
 * non-deleted subscriber and no one else, and a re-publish is absorbed by the dedup constraint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class NotificationFanoutIT extends SharedMySqlContainerSupport {


    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    @Test
    @Transactional
    @DisplayName("publish fan-out inserts one row per enabled non-author non-deleted subscriber, and dedups a re-publish")
    void publishFanoutSingleStatement_WhenSubscribersVary_InsertsOnlyEligibleThenDedups() {
        Long author = enabledUser("author").getId();
        Long enabledA = enabledUser("enabled-a").getId();
        Long enabledB = enabledUser("enabled-b").getId();
        Long disabled = settingsUser("disabled", false).getId();
        Long deletedButEnabled = deletedEnabledUser("deleted").getId();

        UUID plannerId = UUID.randomUUID();

        int inserted = notificationRepository.insertPublishedFanout(author, plannerId.toString(), "Fan Build");

        assertThat(inserted).isEqualTo(2);
        assertThat(unread(enabledA)).isEqualTo(1);
        assertThat(unread(enabledB)).isEqualTo(1);
        assertThat(unread(author)).isZero();
        assertThat(unread(disabled)).isZero();
        assertThat(unread(deletedButEnabled)).isZero();

        int rePublished =
                notificationRepository.insertPublishedFanout(author, plannerId.toString(), "Fan Build");

        assertThat(rePublished).as("uk_notification_dedup absorbs the re-publish").isZero();
        assertThat(unread(enabledA)).isEqualTo(1);
    }

    /**
     * {@code uk_notification_dedup} is what makes a re-dispatch write nothing the second time, and
     * the property the relay depends on is that no preceding read is involved.
     *
     * <p>The soft-deleted case is the one that can drift: the key does not include
     * {@code deleted_at}, so a soft-deleted row still occupies it and a replayed dispatch must
     * still write nothing.</p>
     */
    @Test
    @Transactional
    @DisplayName("the dedup key refuses the second write, soft-deleted rows included")
    void dedupKey_WhenARowAlreadyOccupiesIt_RefusesTheSecondWriteIncludingSoftDeleted() {
        Long recipient = enabledUser("dedup").getId();
        UUID plannerId = UUID.randomUUID();
        String contentId = plannerId.toString();

        assertThat(raise(recipient, contentId, NotificationType.PLANNER_RECOMMENDED, plannerId))
                .as("nothing occupies the key yet")
                .isEqualTo(1);

        assertThat(raise(recipient, contentId, NotificationType.PLANNER_RECOMMENDED, plannerId))
                .as("the key is occupied, so the replayed statement writes nothing")
                .isZero();

        assertThat(raise(recipient, contentId, NotificationType.COMMENT_RECEIVED, plannerId))
                .as("a different type is a different key")
                .isEqualTo(1);

        Notification raised = notificationRepository
                .findByUserIdAndContentIdAndNotificationType(
                        recipient, contentId, NotificationType.PLANNER_RECOMMENDED)
                .orElseThrow();
        raised.softDelete();
        notificationRepository.saveAndFlush(raised);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM notifications WHERE id = ?", java.sql.Timestamp.class, raised.getId()))
                .as("the row reached the database soft-deleted, so the assertion below is not vacuous")
                .isNotNull();

        assertThat(raise(recipient, contentId, NotificationType.PLANNER_RECOMMENDED, plannerId))
                .as("a soft-deleted row still occupies the key, so it still refuses the write")
                .isZero();
    }

    private int raise(Long userId, String contentId, NotificationType type, UUID plannerId) {
        return notificationRepository.insertIgnore(userId, contentId, type.name(),
                plannerId.toString(), "Recommended Build", null, null);
    }

    private long unread(Long userId) {
        return notificationRepository.countByUserIdAndReadFalseAndDeletedAtIsNull(userId);
    }

    private User enabledUser(String name) {
        return settingsUser(name, true);
    }

    private User settingsUser(String name, boolean notifyNewPublications) {
        User user = TestDataFactory.createTestUser(userRepository, name + "-" + UUID.randomUUID() + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO user_settings (user_id, notify_new_publications) VALUES (?, ?)",
                user.getId(), notifyNewPublications);
        return user;
    }

    private User deletedEnabledUser(String name) {
        User user = enabledUser(name);
        jdbcTemplate.update("UPDATE users SET deleted_at = NOW() WHERE id = ?", user.getId());
        return user;
    }
}

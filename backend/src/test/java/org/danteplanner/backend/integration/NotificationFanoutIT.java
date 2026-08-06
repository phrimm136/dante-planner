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
     * The dispatch pre-check answers for exactly the rows {@code uk_notification_dedup} rejects.
     *
     * <p>A pre-check narrower than the key would let a dispatch through to fire the violation it
     * exists to avoid, which under joined propagation reaches the caller as an
     * {@code UnexpectedRollbackException}. The soft-deleted case is the one that can drift: the key
     * does not include {@code deleted_at}, so a soft-deleted row still occupies it.</p>
     */
    @Test
    @Transactional
    @DisplayName("the duplicate pre-check answers for the same rows the dedup key rejects")
    void dedupPreCheck_WhenARowOccupiesTheKey_MatchesTheConstraintIncludingSoftDeleted() {
        Long recipient = enabledUser("precheck").getId();
        UUID plannerId = UUID.randomUUID();
        String contentId = plannerId.toString();

        assertThat(notificationRepository.existsByUserIdAndContentIdAndNotificationType(
                recipient, contentId, NotificationType.PLANNER_RECOMMENDED))
                .as("nothing occupies the key yet")
                .isFalse();

        Notification raised = notificationRepository.save(new Notification(
                recipient, contentId, NotificationType.PLANNER_RECOMMENDED,
                plannerId, "Recommended Build", null, null));

        assertThat(notificationRepository.existsByUserIdAndContentIdAndNotificationType(
                recipient, contentId, NotificationType.PLANNER_RECOMMENDED))
                .as("the row now occupies the key")
                .isTrue();

        assertThat(notificationRepository.existsByUserIdAndContentIdAndNotificationType(
                recipient, contentId, NotificationType.COMMENT_RECEIVED))
                .as("a different type is a different key")
                .isFalse();

        raised.softDelete();
        notificationRepository.saveAndFlush(raised);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM notifications WHERE id = ?", java.sql.Timestamp.class, raised.getId()))
                .as("the row reached the database soft-deleted, so the assertion below is not vacuous")
                .isNotNull();

        assertThat(notificationRepository.existsByUserIdAndContentIdAndNotificationType(
                recipient, contentId, NotificationType.PLANNER_RECOMMENDED))
                .as("a soft-deleted row still occupies the key, so it still counts as carried")
                .isTrue();
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

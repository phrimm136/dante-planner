package org.danteplanner.backend.integration;

import java.util.UUID;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registerSharedMysql(registry, "notification_fanout_it");
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        notificationRepository.deleteAll();
        userSettingsRepository.deleteAll();
        userRepository.deleteAll();
    }

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

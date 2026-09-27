package org.danteplanner.backend.integration;

import jakarta.persistence.EntityManagerFactory;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.repository.UserSettingsRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.service.UserSettingsService;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code user_settings} row as seen from its {@link User}: loading a user does not read it,
 * and purging the user removes it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class UserSettingsPersistenceIT {

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        SharedMySqlContainerSupport.registerOwnDatabase(registry, "user_settings_persistence");
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private UserSettingsService userSettingsService;

    @Autowired
    private UserAccountLifecycleService lifecycleService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;
    private Long userId;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        userId = TestDataFactory.createTestUser(userRepository, "settings-owner@example.com").getId();
        userSettingsService.getOrCreateEntity(userId);
    }

    @Test
    @DisplayName("hard delete removes the purged user's settings row")
    void settingsRow_WhenUserHardDeleted_IsRemoved() {
        assertThat(userSettingsRepository.findByUserId(userId)).as("the settings row exists").isPresent();

        User managed = userRepository.findById(userId).orElseThrow();
        managed.softDelete(Instant.now().minusSeconds(60));
        userRepository.save(managed);
        assertThat(lifecycleService.performHardDelete(userId, Instant.now())).isTrue();

        assertThat(userRepository.findById(userId)).as("the user row is gone").isEmpty();
        assertThat(userSettingsRepository.findByUserId(userId)).as("the settings row is gone").isEmpty();
    }

    @Test
    @DisplayName("loading a user by id issues one statement")
    void userLoad_WhenFoundById_PreparesOneStatement() {
        statistics.clear();
        assertThat(userRepository.findById(userId)).isPresent();

        assertThat(statistics.getPrepareStatementCount())
                .as("statements prepared by findById")
                .isEqualTo(1);
    }
}

package org.danteplanner.backend.user;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.api.UserProfile;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class UserProfileImmutableIT extends SharedMySqlContainerSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("a reflective change to a loaded UserProfile is not written at commit")
    void userProfile_WhenChangedByReflectionAndCommitted_RunsNoUpdateAndKeepsTheRow() {
        User user = TestDataFactory.createTestUser(userRepository, "profile-immutable@example.com");
        String epithet = user.getUsernameEpithet();
        String suffix = user.getUsernameSuffix();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean statisticsWereEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            long updatesBefore = statistics.getEntityStatistics(UserProfile.class.getName()).getUpdateCount();

            transactionTemplate.executeWithoutResult(status -> {
                UserProfile profile = entityManager.find(UserProfile.class, user.getId());
                assertThat(profile.getUsernameEpithet()).isEqualTo(epithet);
                ReflectionTestUtils.setField(profile, "usernameEpithet", "CHANGED");
                ReflectionTestUtils.setField(profile, "usernameSuffix", "zzzzz");
            });

            assertThat(statistics.getEntityStatistics(UserProfile.class.getName()).getUpdateCount())
                    .isEqualTo(updatesBefore);
        } finally {
            statistics.setStatisticsEnabled(statisticsWereEnabled);
        }

        assertThat(jdbcTemplate.queryForMap(
                "SELECT username_epithet, username_suffix FROM users WHERE id = ?", user.getId()))
                .containsEntry("username_epithet", epithet)
                .containsEntry("username_suffix", suffix);
    }
}

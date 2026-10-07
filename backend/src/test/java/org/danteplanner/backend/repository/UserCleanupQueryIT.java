package org.danteplanner.backend.repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class UserCleanupQueryIT extends SharedMySqlContainerSupport {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByPermanentDeleteScheduledAtBefore_WhenSchedulesStraddleTheCutoff_ReturnsOnlyThoseStrictlyBefore() {
        Instant cutoff = Instant.now().plus(365, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Long before = scheduledFor("before@example.com", cutoff.minusSeconds(1));
        Long atCutoff = scheduledFor("at-cutoff@example.com", cutoff);
        Long after = scheduledFor("after@example.com", cutoff.plus(30, ChronoUnit.DAYS));
        Long unscheduled = TestDataFactory.createTestUser(userRepository, "unscheduled@example.com").getId();

        List<Long> found = userRepository.findByPermanentDeleteScheduledAtBefore(cutoff).stream()
                .map(User::getId)
                .toList();

        assertThat(found).contains(before).doesNotContain(atCutoff, after, unscheduled);
    }

    private Long scheduledFor(String email, Instant permanentDeleteAt) {
        User user = TestDataFactory.createTestUser(userRepository, email);
        user.softDelete(permanentDeleteAt);
        return userRepository.save(user).getId();
    }
}

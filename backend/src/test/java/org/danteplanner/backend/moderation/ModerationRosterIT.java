package org.danteplanner.backend.moderation;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import org.danteplanner.backend.moderation.service.ModerationQueryService;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The moderation roster is narrowed by the database, not by the application.
 *
 * <p>Both arms are asserted against real SQL: the account that must appear, and the two kinds that
 * must not — a soft-deleted account, and the account the query is told to exclude (the sentinel, in
 * production).</p>
 */
@SpringBootTest
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class ModerationRosterIT extends SharedMySqlContainerSupport {

    @Autowired
    private ModerationQueryService moderationQueryService;

    @Autowired
    private UserRepository userRepository;

    private List<Long> idsOf(List<User> users) {
        return users.stream().map(User::getId).toList();
    }

    @Test
    void roster_WhenAccountSoftDeleted_OmitsIt() {
        User active = TestDataFactory.createTestUser(userRepository, "active@example.com");
        User removed = TestDataFactory.createTestUser(userRepository, "removed@example.com");
        removed.softDelete(Instant.now().plus(30, ChronoUnit.DAYS));
        userRepository.save(removed);

        List<Long> roster = moderationQueryService.getAllUsers().stream().map(UserSummary::id).toList();

        assertThat(roster).contains(active.getId());
        assertThat(roster).doesNotContain(removed.getId());
    }

    @Test
    void rosterQuery_WhenAccountExcluded_OmitsIt() {
        User kept = TestDataFactory.createTestUser(userRepository, "kept@example.com");
        User excluded = TestDataFactory.createTestUser(userRepository, "excluded@example.com");

        List<Long> roster = idsOf(userRepository.findByDeletedAtIsNullAndIdNot(excluded.getId()));

        assertThat(roster).contains(kept.getId());
        assertThat(roster).doesNotContain(excluded.getId());
    }
}

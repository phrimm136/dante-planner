package org.danteplanner.backend.shared.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.user.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Verifies that the sentinel user (id=0) exists in the database at application startup.
 * The sentinel user is required for vote reassignment during user hard-delete operations.
 *
 * @see org.danteplanner.backend.planner.repository.PlannerVoteRepository#reassignUserVotes
 * @see org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository#reassignUserVotes
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!test & !it") // Skip verification in test environments (TestDataInitializer handles it)
public class SentinelUserVerifier implements ApplicationRunner {

    /**
     * Sentinel user ID used for anonymizing deleted users' votes.
     * Must match the value in UserAccountLifecycleService.
     */
    private static final Long SENTINEL_USER_ID = 0L;

    private final UserRepository userRepository;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Verifying sentinel user (id={}) exists...", SENTINEL_USER_ID);

        if (!userRepository.existsById(SENTINEL_USER_ID)) {
            throw new IllegalStateException(
                String.format(
                    "Sentinel user (id=%d) is missing from the database. " +
                    "This user is required for vote reassignment during user deletion. " +
                    "Please run the appropriate database migration to create the sentinel user.",
                    SENTINEL_USER_ID
                )
            );
        }

        log.info("Sentinel user verification passed");
    }
}

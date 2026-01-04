package org.danteplanner.backend.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.service.UserAccountLifecycleService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled job for permanently deleting users whose grace period has expired.
 * Runs daily at 3 AM by default (configurable via app.user.cleanup.cron).
 *
 * <p>Note: This is a single-server solution. For multi-server deployments,
 * consider using ShedLock or similar distributed locking mechanism.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserCleanupScheduler {

    private final UserRepository userRepository;
    private final UserAccountLifecycleService lifecycleService;

    /**
     * Find and permanently delete all users whose grace period has expired.
     * Each user's votes are reassigned to the sentinel user before deletion
     * to preserve vote counts on planners.
     */
    @Scheduled(cron = "${app.user.cleanup.cron:0 0 3 * * *}")
    public void cleanupExpiredUsers() {
        log.info("Starting scheduled user cleanup job");

        List<User> expiredUsers = userRepository.findByPermanentDeleteScheduledAtBefore(Instant.now());

        if (expiredUsers.isEmpty()) {
            log.info("No expired users to clean up");
            return;
        }

        log.info("Found {} expired users to hard-delete", expiredUsers.size());

        int successCount = 0;
        int failureCount = 0;

        for (User user : expiredUsers) {
            try {
                lifecycleService.performHardDelete(user);
                successCount++;
                log.info("Hard-deleted user {}", user.getId());
            } catch (Exception e) {
                failureCount++;
                log.error("Failed to hard-delete user {}: {}", user.getId(), e.getMessage(), e);
            }
        }

        log.info("Completed user cleanup job: {} deleted, {} failed", successCount, failureCount);
    }
}

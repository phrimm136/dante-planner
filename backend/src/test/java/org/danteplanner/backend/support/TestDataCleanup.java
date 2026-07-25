package org.danteplanner.backend.support;

import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.support.TestDataCleanup;

/**
 * Shared teardown for tests that share a database.
 *
 * <p>Only the parts that are the same everywhere live here. Repository deletions stay in each test,
 * because the set to clear and the order to clear it in follow that test's own foreign keys.</p>
 */
public final class TestDataCleanup {

    private TestDataCleanup() {
    }

    /**
     * Delete every user except the sentinel that soft-deleted content is reassigned to. Removing it
     * breaks the foreign key that reassignment depends on, so it must survive every teardown.
     *
     * @param userRepository the repository to sweep
     */
    public static void deleteUsersExceptSentinel(UserRepository userRepository) {
        userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(UserAccountLifecycleService.SENTINEL_USER_ID))
                .forEach(userRepository::delete);
    }
}

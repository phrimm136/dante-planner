package org.danteplanner.backend.planner.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.user.exception.UserBannedException;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.user.exception.UserTimedOutException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Shared access guard for planner ownership and user-restriction checks.
 *
 * <p>Centralizes the guard logic used across the planner command, query,
 * publishing, and engagement services. Pairs with {@link Planner#isOwnedBy(Long)}.</p>
 */
@Service
@RequiredArgsConstructor
public class PlannerAccessGuard {

    private final UserRepository userRepository;
    private final PlannerRepository plannerRepository;

    /**
     * Get user and check if restricted (timed out or banned).
     * Returns the user to avoid duplicate DB queries.
     *
     * @param userId the user ID
     * @return the User entity (not restricted)
     * @throws UserNotFoundException if user not found
     * @throws UserTimedOutException if user is currently timed out
     * @throws UserBannedException   if user is currently banned
     */
    public User getUserAndCheckRestrictions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.isTimedOut()) {
            throw new UserTimedOutException(userId, user.getTimeoutUntil());
        }

        if (user.isBanned()) {
            throw new UserBannedException(user.getId(), user.getBannedAt());
        }

        return user;
    }

    /**
     * Check if user is restricted (timed out or banned).
     * Called at the start of write operations that don't need the User entity.
     *
     * @param userId the user ID
     * @throws UserNotFoundException if user not found
     * @throws UserTimedOutException if user is currently timed out
     * @throws UserBannedException   if user is currently banned
     */
    public void checkUserRestrictions(Long userId) {
        getUserAndCheckRestrictions(userId);
    }

    /**
     * Find a planner owned by the given user, or throw.
     *
     * @param userId the user ID
     * @param id     the planner ID
     * @return the planner
     * @throws PlannerNotFoundException if planner not found for this user
     */
    public Planner findPlannerOrThrow(Long userId, UUID id) {
        return plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new PlannerNotFoundException(id));
    }
}

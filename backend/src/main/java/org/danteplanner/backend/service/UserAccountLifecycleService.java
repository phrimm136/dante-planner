package org.danteplanner.backend.service;

import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.exception.UserNotFoundException;
import org.danteplanner.backend.repository.PlannerCommentRepository;
import org.danteplanner.backend.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.repository.PlannerVoteRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Service responsible for user account lifecycle operations.
 *
 * <p>Handles:
 * <ul>
 *   <li>Soft-delete: Mark account as deleted with grace period</li>
 *   <li>Reactivation: Restore soft-deleted account during grace period</li>
 *   <li>Hard-delete: Permanently remove user and reassign votes</li>
 * </ul>
 *
 * <p>Separated from {@link UserService} to follow Single Responsibility Principle.
 */
@Service
public class UserAccountLifecycleService {

    /**
     * Sentinel user ID used to preserve vote counts after user deletion.
     * This user is created in the migration V009__add_user_soft_delete.sql.
     */
    public static final Long SENTINEL_USER_ID = 0L;

    private final UserRepository userRepository;
    private final PlannerVoteRepository plannerVoteRepository;
    private final PlannerCommentRepository plannerCommentRepository;
    private final PlannerCommentVoteRepository plannerCommentVoteRepository;
    private final int gracePeriodDays;

    public UserAccountLifecycleService(
            UserRepository userRepository,
            PlannerVoteRepository plannerVoteRepository,
            PlannerCommentRepository plannerCommentRepository,
            PlannerCommentVoteRepository plannerCommentVoteRepository,
            @Value("${app.user.deletion.grace-period-days:30}") int gracePeriodDays) {
        this.userRepository = userRepository;
        this.plannerVoteRepository = plannerVoteRepository;
        this.plannerCommentRepository = plannerCommentRepository;
        this.plannerCommentVoteRepository = plannerCommentVoteRepository;
        this.gracePeriodDays = gracePeriodDays;
    }

    /**
     * Soft-delete a user account with a scheduled permanent deletion date.
     * The account is immediately blocked from authentication, but data is preserved
     * for the grace period to allow reactivation via re-login.
     *
     * @param userId the user ID
     * @return the scheduled permanent delete date
     * @throws UserNotFoundException if user not found
     */
    @Transactional
    public Instant deleteAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.isDeleted()) {
            // Idempotent: return existing scheduled date
            return user.getPermanentDeleteScheduledAt();
        }

        Instant scheduledDeleteAt = Instant.now().plus(Duration.ofDays(gracePeriodDays));
        user.softDelete(scheduledDeleteAt);
        userRepository.save(user);

        return scheduledDeleteAt;
    }

    /**
     * Reactivate a soft-deleted user account during the grace period.
     * Called when a deleted user re-authenticates via OAuth.
     *
     * @param userId the user ID
     * @throws UserNotFoundException if user not found
     */
    @Transactional
    public void reactivateAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.isDeleted()) {
            return; // Already active, idempotent no-op
        }

        user.reactivate();
        userRepository.save(user);
    }

    /**
     * Permanently delete a user and reassign their votes and comments to the sentinel user.
     * This preserves vote counts and comment content while anonymizing the author.
     * CASCADE will delete the user's planners.
     *
     * @param user the user to permanently delete
     */
    @Transactional
    public void performHardDelete(User user) {
        Long userId = user.getId();

        // Reassign planner votes to sentinel user (preserves vote counts)
        // Uses immutable vote pattern - votes remain but change user_id only
        plannerVoteRepository.reassignUserVotes(userId, SENTINEL_USER_ID);

        // Reassign comment votes to sentinel user (preserves vote counts)
        plannerCommentVoteRepository.reassignUserVotes(userId, SENTINEL_USER_ID);

        // Reassign comments to sentinel user (preserves comment content)
        plannerCommentRepository.reassignCommentsToSentinel(userId, SENTINEL_USER_ID);

        // Now delete user (CASCADE will delete their planners)
        userRepository.delete(user);
    }
}

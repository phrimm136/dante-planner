package org.danteplanner.backend.service;

import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.exception.UserNotFoundException;
import org.danteplanner.backend.repository.PlannerCommentRepository;
import org.danteplanner.backend.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.repository.PlannerVoteRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.service.token.TokenBlacklistService;
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
     * Sentinel user that inherits a deleted user's votes and comments to anonymize them.
     * Upvote counts are denormalized counter columns, independent of vote rows, so
     * reassignment does not change them.
     * This user is created in the migration V009__add_user_soft_delete.sql.
     */
    public static final Long SENTINEL_USER_ID = 0L;

    private final UserRepository userRepository;
    private final PlannerVoteRepository plannerVoteRepository;
    private final PlannerCommentRepository plannerCommentRepository;
    private final PlannerCommentVoteRepository plannerCommentVoteRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final int gracePeriodDays;

    public UserAccountLifecycleService(
            UserRepository userRepository,
            PlannerVoteRepository plannerVoteRepository,
            PlannerCommentRepository plannerCommentRepository,
            PlannerCommentVoteRepository plannerCommentVoteRepository,
            TokenBlacklistService tokenBlacklistService,
            @Value("${app.user.deletion.grace-period-days:30}") int gracePeriodDays) {
        this.userRepository = userRepository;
        this.plannerVoteRepository = plannerVoteRepository;
        this.plannerCommentRepository = plannerCommentRepository;
        this.plannerCommentVoteRepository = plannerCommentVoteRepository;
        this.tokenBlacklistService = tokenBlacklistService;
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

        // Immediately revoke existing tokens via the in-memory invalidation check.
        // The JWT filter no longer does a per-request DB lookup to detect deletion
        // (token-only auth), so deletion must push the revocation signal here.
        tokenBlacklistService.invalidateUserTokens(userId);

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
     * This anonymizes the author while preserving comment content. Upvote counts are
     * denormalized counters, independent of vote rows, so they are unaffected.
     * CASCADE will delete the user's planners.
     *
     * @param user the user to permanently delete
     */
    @Transactional
    public void performHardDelete(User user) {
        Long userId = user.getId();

        // Drop votes that collide with the sentinel's existing votes on the same target,
        // then reassign the rest. Reassigning a collision would duplicate the composite PK.
        // The displayed count lives in the denormalized counter, so it is unaffected.
        plannerVoteRepository.deleteVotesCollidingWithSentinel(userId, SENTINEL_USER_ID);
        plannerVoteRepository.reassignUserVotes(userId, SENTINEL_USER_ID);

        plannerCommentVoteRepository.deleteVotesCollidingWithSentinel(userId, SENTINEL_USER_ID);
        plannerCommentVoteRepository.reassignUserVotes(userId, SENTINEL_USER_ID);

        // Reassign comments to sentinel user (preserves comment content)
        plannerCommentRepository.reassignCommentsToSentinel(userId, SENTINEL_USER_ID);

        // Now delete user (CASCADE will delete their planners)
        userRepository.delete(user);
    }
}

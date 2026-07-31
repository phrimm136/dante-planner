package org.danteplanner.backend.user.service;

import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.comment.service.CommentAccountPurgeService;
import org.danteplanner.backend.moderation.service.ModerationAccountPurgeService;
import org.danteplanner.backend.planner.service.PlannerAccountPurgeService;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
    private final PlannerAccountPurgeService plannerAccountPurgeService;
    private final PlannerCatalogService plannerCatalogService;
    private final CommentAccountPurgeService commentAccountPurgeService;
    private final ModerationAccountPurgeService moderationAccountPurgeService;
    private final TokenBlacklistService tokenBlacklistService;
    private final int gracePeriodDays;

    public UserAccountLifecycleService(
            UserRepository userRepository,
            PlannerAccountPurgeService plannerAccountPurgeService,
            PlannerCatalogService plannerCatalogService,
            CommentAccountPurgeService commentAccountPurgeService,
            ModerationAccountPurgeService moderationAccountPurgeService,
            TokenBlacklistService tokenBlacklistService,
            @Value("${app.user.deletion.grace-period-days:30}") int gracePeriodDays) {
        this.userRepository = userRepository;
        this.plannerAccountPurgeService = plannerAccountPurgeService;
        this.plannerCatalogService = plannerCatalogService;
        this.commentAccountPurgeService = commentAccountPurgeService;
        this.moderationAccountPurgeService = moderationAccountPurgeService;
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

        // Withdrawn from the public listing, not unpublished: reactivation within the grace
        // period restores the same set without the owner republishing.
        plannerCatalogService.hideAllOwnedBy(userId);

        // Immediately revoke existing tokens via the in-memory invalidation check.
        // Auth is token-only: the JWT filter does no per-request DB lookup, so deletion
        // must push the revocation signal here.
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
        plannerCatalogService.restoreAllOwnedBy(userId);
    }

    /**
     * Permanently delete a user and reassign their votes and comments to the sentinel user.
     * This anonymizes the author while preserving comment content. Upvote counts are
     * denormalized counters, independent of vote rows, so they are unaffected.
     * Planner satellite/projection/filter rows are swept app-side; the user-row
     * CASCADE removes the planner cores and FK-bearing children.
     *
     * @param user the user to permanently delete
     */
    @Transactional
    public void performHardDelete(User user) {
        Long userId = user.getId();

        plannerAccountPurgeService.reassignVotesToSentinel(userId, SENTINEL_USER_ID);
        commentAccountPurgeService.reassignAuthorshipToSentinel(userId, SENTINEL_USER_ID);

        List<UUID> plannerIds = plannerAccountPurgeService.plannerIdsOwnedBy(userId);
        if (!plannerIds.isEmpty()) {
            // Reports first: their no-action FKs would block the cascade the user delete relies on.
            moderationAccountPurgeService.deleteReportsFor(plannerIds);
            plannerAccountPurgeService.deleteProjectionsFor(plannerIds);
        }

        // The user row's CASCADE removes the planner cores and their FK-bearing children.
        userRepository.delete(user);
    }
}

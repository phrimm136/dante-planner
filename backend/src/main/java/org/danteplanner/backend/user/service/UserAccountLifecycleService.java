package org.danteplanner.backend.user.service;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.comment.service.CommentAccountPurgeService;
import org.danteplanner.backend.moderation.service.ModerationAccountPurgeService;
import org.danteplanner.backend.planner.service.PlannerAccountPurgeService;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
@Slf4j
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

        // Withdrawn from the public listing, not unpublished: reactivation within the grace
        // period restores the same set without the owner republishing.
        plannerCatalogService.hideAllOwnedBy(userId);

        // Immediately revoke existing tokens via the in-memory invalidation check.
        // Auth is token-only: the JWT filter does no per-request DB lookup, so deletion
        // must push the revocation signal here.
        tokenBlacklistService.invalidateUserTokens(userId);
        log.info("User {} requested account deletion", userId);

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
        plannerCatalogService.restoreAllOwnedBy(userId);
    }

    /**
     * Permanently delete a user and reassign their votes, comments, and moderation actions to
     * the sentinel user. This anonymizes the author while preserving comment content. Upvote
     * counts are denormalized counters, independent of vote rows, so they are unaffected.
     * Planner satellite/projection/filter rows are swept app-side; the user-row
     * CASCADE removes the planner cores and FK-bearing children.
     *
     * <p>Eligibility is re-read under a row lock rather than trusted from the caller: the
     * candidate list is built outside this transaction and may be served by a replica, so an
     * account reactivated in between is still listed. A no-longer-eligible account is skipped.</p>
     *
     * @param userId the account to permanently delete
     * @param cutoff the instant the grace period must have expired before
     * @return true if the account was deleted, false if it was no longer eligible
     */
    @Transactional
    public boolean performHardDelete(Long userId, Instant cutoff) {
        Optional<User> purgeable = userRepository.findWithLockPurgeable(userId, cutoff);
        if (purgeable.isEmpty()) {
            return false;
        }
        User user = purgeable.get();

        // Auth is token-only, so a surviving cookie would outlive the row it names. Guarded
        // here rather than inside the service: logout-everywhere depends on this write failing
        // loudly, while a purge blocked on an unreachable Redis would strand every candidate
        // past its retention deadline.
        try {
            tokenBlacklistService.invalidateUserTokens(userId);
        } catch (DataAccessException e) {
            log.warn("Token invalidation unavailable for purged user {}; tokens lapse at expiry",
                    userId, e);
        }

        plannerAccountPurgeService.reassignVotesToSentinel(userId, SENTINEL_USER_ID);
        commentAccountPurgeService.reassignAuthorshipToSentinel(userId, SENTINEL_USER_ID);
        moderationAccountPurgeService.reassignActionsToSentinel(userId, SENTINEL_USER_ID);

        List<UUID> plannerIds = plannerAccountPurgeService.plannerIdsOwnedBy(userId);
        if (!plannerIds.isEmpty()) {
            // Reports first: their no-action FKs would block the cascade the user delete relies on.
            moderationAccountPurgeService.deleteReportsFor(plannerIds);
            plannerAccountPurgeService.deleteProjectionsFor(plannerIds);
        }

        // The user row's CASCADE removes the planner cores and their FK-bearing children.
        userRepository.delete(user);
        return true;
    }
}

package org.danteplanner.backend.user.service;

import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.moderation.repository.PlannerCommentReportRepository;
import org.danteplanner.backend.moderation.repository.PlannerReportRepository;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerContentRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerModerationRepository;
import org.danteplanner.backend.planner.repository.PlannerPublicationRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
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
    private final PlannerRepository plannerRepository;
    private final PlannerContentRepository plannerContentRepository;
    private final PlannerPublicationRepository plannerPublicationRepository;
    private final PlannerModerationRepository plannerModerationRepository;
    private final PlannerStatsRepository plannerStatsRepository;
    private final PlannerCatalogRepository plannerCatalogRepository;
    private final PlannerEntityFilterRepository plannerEntityFilterRepository;
    private final PlannerKeywordFilterRepository plannerKeywordFilterRepository;
    private final PlannerVoteRepository plannerVoteRepository;
    private final PlannerCommentRepository plannerCommentRepository;
    private final PlannerCommentVoteRepository plannerCommentVoteRepository;
    private final PlannerReportRepository plannerReportRepository;
    private final PlannerCommentReportRepository plannerCommentReportRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final int gracePeriodDays;

    public UserAccountLifecycleService(
            UserRepository userRepository,
            PlannerRepository plannerRepository,
            PlannerContentRepository plannerContentRepository,
            PlannerPublicationRepository plannerPublicationRepository,
            PlannerModerationRepository plannerModerationRepository,
            PlannerStatsRepository plannerStatsRepository,
            PlannerCatalogRepository plannerCatalogRepository,
            PlannerEntityFilterRepository plannerEntityFilterRepository,
            PlannerKeywordFilterRepository plannerKeywordFilterRepository,
            PlannerVoteRepository plannerVoteRepository,
            PlannerCommentRepository plannerCommentRepository,
            PlannerCommentVoteRepository plannerCommentVoteRepository,
            PlannerReportRepository plannerReportRepository,
            PlannerCommentReportRepository plannerCommentReportRepository,
            TokenBlacklistService tokenBlacklistService,
            @Value("${app.user.deletion.grace-period-days:30}") int gracePeriodDays) {
        this.userRepository = userRepository;
        this.plannerRepository = plannerRepository;
        this.plannerContentRepository = plannerContentRepository;
        this.plannerPublicationRepository = plannerPublicationRepository;
        this.plannerModerationRepository = plannerModerationRepository;
        this.plannerStatsRepository = plannerStatsRepository;
        this.plannerCatalogRepository = plannerCatalogRepository;
        this.plannerEntityFilterRepository = plannerEntityFilterRepository;
        this.plannerKeywordFilterRepository = plannerKeywordFilterRepository;
        this.plannerVoteRepository = plannerVoteRepository;
        this.plannerCommentRepository = plannerCommentRepository;
        this.plannerCommentVoteRepository = plannerCommentVoteRepository;
        this.plannerReportRepository = plannerReportRepository;
        this.plannerCommentReportRepository = plannerCommentReportRepository;
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
     * Planner satellite/projection/filter rows are swept app-side; the user-row
     * CASCADE removes the planner cores and FK-bearing children.
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

        // Satellite, projection, and filter rows carry no FK to the planner core,
        // so they are swept app-side by planner id before the user delete cascades
        // the core rows (and the FK-bearing child tables) away.
        List<UUID> plannerIds = plannerRepository.findIdsByUserId(userId);
        if (!plannerIds.isEmpty()) {
            // Reports carry no-action FKs (to the core and to comments) and would
            // block the cascade the user delete relies on
            plannerCommentReportRepository.deleteAllByPlannerIds(plannerIds);
            plannerReportRepository.deleteAllByPlannerIds(plannerIds);
            plannerEntityFilterRepository.deleteAllByPlannerIds(plannerIds);
            plannerKeywordFilterRepository.deleteAllByPlannerIds(plannerIds);
            plannerCatalogRepository.deleteAllByPlannerIds(plannerIds);
            plannerStatsRepository.deleteAllByPlannerIds(plannerIds);
            plannerModerationRepository.deleteAllByPlannerIds(plannerIds);
            plannerPublicationRepository.deleteAllByPlannerIds(plannerIds);
            plannerContentRepository.deleteAllByPlannerIds(plannerIds);
        }

        // Now delete user (CASCADE will delete their planner cores and FK children)
        userRepository.delete(user);
    }
}

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
 */
@Service
@Slf4j
public class UserAccountLifecycleService {

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

    @Transactional
    public Instant deleteAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.isDeleted()) {
            return user.getPermanentDeleteScheduledAt();
        }

        Instant scheduledDeleteAt = Instant.now().plus(Duration.ofDays(gracePeriodDays));
        user.softDelete(scheduledDeleteAt);

        plannerCatalogService.hideAllOwnedBy(userId);

        // Immediately revoke existing tokens via the in-memory invalidation check.
        // Auth is token-only: the JWT filter does no per-request DB lookup, so deletion
        // must push the revocation signal here.
        tokenBlacklistService.invalidateUserTokens(userId);
        log.info("User {} requested account deletion", userId);

        return scheduledDeleteAt;
    }

    @Transactional
    public void reactivateAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.isDeleted()) {
            return;
        }

        user.reactivate();
        plannerCatalogService.restoreAllOwnedBy(userId);
    }

    @Transactional
    public boolean performHardDelete(Long userId, Instant cutoff) {
        Optional<User> purgeable = userRepository.findWithLockPurgeable(userId, cutoff);
        if (purgeable.isEmpty()) {
            return false;
        }
        User user = purgeable.get();

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

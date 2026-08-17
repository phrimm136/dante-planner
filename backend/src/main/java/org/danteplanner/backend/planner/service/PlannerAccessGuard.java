package org.danteplanner.backend.planner.service;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.user.exception.UserBannedException;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.user.exception.UserTimedOutException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/**
 * Shared access guard for planner ownership and user-restriction checks.
 *
 * <p>Centralizes the guard logic used across the planner command, query,
 * publishing, and engagement services. Pairs with {@link Planner#isOwnedBy(Long)}.</p>
 */
@Service
public class PlannerAccessGuard {

    private final UserService userService;
    private final PlannerRepository plannerRepository;
    private final Clock clock;

    @Autowired
    public PlannerAccessGuard(UserService userService, PlannerRepository plannerRepository) {
        this(userService, plannerRepository, Clock.systemUTC());
    }

    PlannerAccessGuard(UserService userService, PlannerRepository plannerRepository, Clock clock) {
        this.userService = userService;
        this.plannerRepository = plannerRepository;
        this.clock = clock;
    }

    /**
     * Fetch a user without applying any restriction check. Private planner work stays available
     * under both a timeout and a ban.
     *
     * @param userId the user ID
     * @return the User entity
     * @throws UserNotFoundException if user not found
     */
    public User getUser(Long userId) {
        return userService.findById(userId);
    }

    /**
     * Reject a user who may not contribute public content: publishing, commenting, replying, or
     * editing a comment. Both a timeout and a ban withdraw this.
     *
     * @param userId the user ID
     * @throws UserNotFoundException if user not found
     * @throws UserTimedOutException if user is currently timed out
     * @throws UserBannedException   if user is currently banned
     */
    public void checkNotRestricted(Long userId) {
        User user = getUser(userId);

        switch (user.restrictionState(clock)) {
            case TIMED_OUT -> throw new UserTimedOutException(userId, user.getTimeoutUntil());
            case BANNED -> throw new UserBannedException(user.getId(), user.getBannedAt());
            case ACTIVE -> { }
        }
    }

    /**
     * Reject a banned user from acting on someone else's content: voting and reporting. A timeout
     * does not withdraw this, which is what keeps the two severities distinct.
     *
     * @param userId the user ID
     * @throws UserNotFoundException if user not found
     * @throws UserBannedException   if user is currently banned
     */
    public void checkNotBanned(Long userId) {
        User user = getUser(userId);

        if (user.isBanned()) {
            throw new UserBannedException(user.getId(), user.getBannedAt());
        }
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
        return plannerRepository.findAggregateForOwner(id, userId)
                .orElseThrow(() -> new PlannerNotFoundException(id));
    }

    /**
     * Require a planner to exist, published or not, for callers that apply their own visibility
     * rule to what comes back.
     *
     * @param id the planner ID
     * @return the planner
     * @throws PlannerNotFoundException if no planner carries the id
     */
    public Planner requireExisting(UUID id) {
        return plannerRepository.findAggregate(id)
                .orElseThrow(() -> new PlannerNotFoundException(id));
    }

    /**
     * Require a planner to be publicly visible, whoever is asking, and return it loaded. An
     * unpublished, taken-down, or soft-deleted planner is indistinguishable from a missing one to a
     * non-owner. Callers that discard the return value belong on {@link #checkPublished(UUID)}.
     *
     * @param id the planner ID
     * @return the published planner
     * @throws PlannerNotFoundException if no published planner carries the id
     */
    public Planner requirePublished(UUID id) {
        return plannerRepository.findPublishedAggregate(id)
                .orElseThrow(() -> new PlannerNotFoundException(id));
    }

    /**
     * Assert that a planner is publicly visible, whoever is asking. The void pure-authorization
     * form of {@link #requirePublished(UUID)}: it settles the same visibility question with an
     * existence query, leaving the aggregate unhydrated.
     *
     * @param plannerId the planner ID
     * @throws PlannerNotFoundException if no published planner carries the id
     */
    public void checkPublished(UUID plannerId) {
        if (!plannerRepository.existsPublishedById(plannerId)) {
            throw new PlannerNotFoundException(plannerId);
        }
    }
}

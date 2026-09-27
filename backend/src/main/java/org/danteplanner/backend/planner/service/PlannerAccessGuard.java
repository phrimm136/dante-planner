package org.danteplanner.backend.planner.service;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.user.exception.UserBannedException;
import org.danteplanner.backend.user.exception.UserTimedOutException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

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

    public User getUser(Long userId) {
        return userService.findById(userId);
    }

    public void checkNotRestricted(Long userId) {
        User user = getUser(userId);

        switch (user.restrictionState(clock)) {
            case TIMED_OUT -> throw new UserTimedOutException(userId, user.getTimeoutUntil());
            case BANNED -> throw new UserBannedException(user.getId(), user.getBannedAt());
            case ACTIVE -> { }
        }
    }

    public void holdActiveAccount(Long userId) {
        userService.holdActiveById(userId);
    }

    public void checkNotBanned(Long userId) {
        User user = getUser(userId);

        if (user.isBanned()) {
            throw new UserBannedException(user.getId(), user.getBannedAt());
        }
    }

    public Planner findPlannerOrThrow(Long userId, UUID id) {
        return plannerRepository.findAggregateForOwner(id, userId)
                .orElseThrow(() -> new PlannerNotFoundException(id));
    }

    public Planner requireExisting(UUID id) {
        return plannerRepository.findAggregate(id)
                .orElseThrow(() -> new PlannerNotFoundException(id));
    }

    public Planner requirePublished(UUID id) {
        return plannerRepository.findPublishedAggregate(id)
                .orElseThrow(() -> new PlannerNotFoundException(id));
    }

    public void checkPublished(UUID plannerId) {
        if (!plannerRepository.existsPublishedById(plannerId)) {
            throw new PlannerNotFoundException(plannerId);
        }
    }
}

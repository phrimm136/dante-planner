package org.danteplanner.backend.planner.service;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.user.api.UserBannedException;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.user.api.UserTimedOutException;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class PlannerAccessGuard {

    private final UserService userService;
    private final Users users;
    private final PlannerRepository plannerRepository;
    private final Clock clock;

    @Autowired
    public PlannerAccessGuard(UserService userService, Users users, PlannerRepository plannerRepository) {
        this(userService, users, plannerRepository, Clock.systemUTC());
    }

    PlannerAccessGuard(UserService userService, Users users, PlannerRepository plannerRepository, Clock clock) {
        this.userService = userService;
        this.users = users;
        this.plannerRepository = plannerRepository;
        this.clock = clock;
    }

    public User getUser(Long userId) {
        return userService.findById(userId);
    }

    public void checkNotRestricted(Long userId) {
        UserSummary user = users.findById(userId);

        switch (user.restrictionState(clock)) {
            case TIMED_OUT -> throw new UserTimedOutException(userId, user.timeoutUntil());
            case BANNED -> throw new UserBannedException(user.id(), user.bannedAt());
            case ACTIVE -> { }
        }
    }

    public void holdActiveAccount(Long userId) {
        users.holdActiveById(userId);
    }

    public void checkNotBanned(Long userId) {
        UserSummary user = users.findById(userId);

        if (user.isBanned()) {
            throw new UserBannedException(user.id(), user.bannedAt());
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

    @Transactional(readOnly = true)
    public void checkPublished(UUID plannerId) {
        if (!plannerRepository.existsPublishedById(plannerId)) {
            throw new PlannerNotFoundException(plannerId);
        }
    }
}

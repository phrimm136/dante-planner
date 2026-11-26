package org.danteplanner.backend.planner.service;

import org.danteplanner.backend.planner.api.PlannerAccess;
import org.danteplanner.backend.planner.api.PlannerMiss;
import org.danteplanner.backend.planner.api.PlannerNotFoundException;
import org.danteplanner.backend.planner.api.PlannerThread;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PublishedPlannerRow;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.shared.freshness.ScopedRead;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.user.api.Users;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PlannerAccessGuard implements PlannerAccess {

    private final Users users;
    private final PlannerRepository plannerRepository;

    public PlannerAccessGuard(Users users, PlannerRepository plannerRepository) {
        this.users = users;
        this.plannerRepository = plannerRepository;
    }

    public UserSummary requireUser(Long userId) {
        return users.findById(userId);
    }

    public void holdActiveAccount(Long userId) {
        users.holdActiveById(userId);
    }

    public StoredPlanner findPlannerOrThrow(Long userId, UUID id) {
        return plannerRepository.findAggregateForOwner(id, userId)
                .orElseThrow(() -> absent(id));
    }

    public StoredPlanner requireAggregate(UUID id) {
        return plannerRepository.findAggregate(id)
                .orElseThrow(() -> absent(id));
    }

    @Override
    public PlannerThread requireExisting(UUID id) {
        return plannerRepository.findThread(id)
                .orElseThrow(() -> absent(id));
    }

    public StoredPlanner requirePublished(UUID id) {
        return requirePublishedWithAuthor(id).planner();
    }

    public PublishedPlannerRow requirePublishedWithAuthor(UUID id) {
        PublishedPlannerRow row = plannerRepository.findAggregateWithAuthor(id)
                .orElseThrow(() -> absent(id));
        return switch (row.visibility()) {
            case SHOWN -> row;
            case HIDDEN -> throw hidden(id, row.planner().userId());
        };
    }

    @Override
    @Transactional(readOnly = true)
    public ScopedRead<UUID> checkPublished(UUID plannerId) {
        PlannerThread planner = requireExisting(plannerId);
        return switch (planner.visibility()) {
            case SHOWN -> new ScopedRead<>(plannerId, PlannerVisibility.scopes(plannerId, planner.ownerId()));
            case HIDDEN -> throw hidden(plannerId, planner.ownerId());
        };
    }

    private static PlannerNotFoundException hidden(UUID plannerId, long ownerId) {
        return new PlannerNotFoundException(new PlannerMiss.Hidden(plannerId, ownerId));
    }

    static PlannerNotFoundException absent(UUID id) {
        return new PlannerNotFoundException(new PlannerMiss.Absent(id));
    }
}

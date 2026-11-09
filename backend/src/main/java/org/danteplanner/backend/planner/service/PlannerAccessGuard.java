package org.danteplanner.backend.planner.service;

import org.danteplanner.backend.planner.api.PlannerAccess;
import org.danteplanner.backend.planner.api.PlannerMiss;
import org.danteplanner.backend.planner.api.PlannerNotFoundException;
import org.danteplanner.backend.planner.api.PlannerThread;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PublishedPlannerRow;
import org.danteplanner.backend.shared.freshness.ScopedRead;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.planner.repository.PlannerRepository;
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

    public void requireUser(Long userId) {
        users.findById(userId);
    }

    public void holdActiveAccount(Long userId) {
        users.holdActiveById(userId);
    }

    public Planner findPlannerOrThrow(Long userId, UUID id) {
        return plannerRepository.findAggregateForOwner(id, userId)
                .orElseThrow(() -> absent(id));
    }

    public Planner requireAggregate(UUID id) {
        return plannerRepository.findAggregate(id)
                .orElseThrow(() -> absent(id));
    }

    @Override
    public PlannerThread requireExisting(UUID id) {
        return plannerRepository.findThread(id)
                .orElseThrow(() -> absent(id));
    }

    public Planner requirePublished(UUID id) {
        return requirePublishedWithAuthor(id).planner();
    }

    public PublishedPlannerRow requirePublishedWithAuthor(UUID id) {
        PublishedPlannerRow row = plannerRepository.findAggregateWithAuthor(id)
                .orElseThrow(() -> absent(id));
        requireVisible(new PlannerVisibilityFacts(id, row.planner().getUserId(), row.planner().isPublished(),
                row.author().getDeletedAt() != null));
        return row;
    }

    @Override
    @Transactional(readOnly = true)
    public ScopedRead<UUID> checkPublished(UUID plannerId) {
        PlannerThread planner = requireExisting(plannerId);
        requireVisible(new PlannerVisibilityFacts(plannerId, planner.ownerId(), planner.published(),
                planner.ownerDeactivated()));
        return new ScopedRead<>(plannerId, PlannerVisibility.scopes(plannerId, planner.ownerId()));
    }

    private static void requireVisible(PlannerVisibilityFacts facts) {
        if (!PlannerVisibility.visible(facts)) {
            throw new PlannerNotFoundException(new PlannerMiss.Hidden(facts.plannerId(), facts.ownerId()));
        }
    }

    private static PlannerNotFoundException absent(UUID id) {
        return new PlannerNotFoundException(new PlannerMiss.Absent(id));
    }
}

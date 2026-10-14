package org.danteplanner.backend.planner.service;

import org.danteplanner.backend.planner.api.PlannerAccess;
import org.danteplanner.backend.planner.api.PlannerNotFoundException;
import org.danteplanner.backend.planner.api.PlannerThread;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PublishedPlannerRow;
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
                .orElseThrow(() -> new PlannerNotFoundException(id));
    }

    public Planner requireAggregate(UUID id) {
        return plannerRepository.findAggregate(id)
                .orElseThrow(() -> new PlannerNotFoundException(id));
    }

    @Override
    public PlannerThread requireExisting(UUID id) {
        return plannerRepository.findThread(id)
                .orElseThrow(() -> new PlannerNotFoundException(id));
    }

    public Planner requirePublished(UUID id) {
        return requirePublishedWithAuthor(id).planner();
    }

    public PublishedPlannerRow requirePublishedWithAuthor(UUID id) {
        return plannerRepository.findPublishedAggregate(id)
                .orElseThrow(() -> new PlannerNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public void checkPublished(UUID plannerId) {
        if (!plannerRepository.existsPublishedById(plannerId)) {
            throw new PlannerNotFoundException(plannerId);
        }
    }
}

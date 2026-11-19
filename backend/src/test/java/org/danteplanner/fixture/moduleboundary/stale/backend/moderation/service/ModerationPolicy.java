package org.danteplanner.fixture.moduleboundary.stale.backend.moderation.service;

import org.danteplanner.backend.planner.repository.PlannerRepository;

public record ModerationPolicy(PlannerRepository plannerRepository) {
}

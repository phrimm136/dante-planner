package org.danteplanner.fixture.moduleboundary.mixed.backend.moderation.service;

import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.user.entity.User;

public record UserModerationService(User user, PlannerRepository plannerRepository) {
}

package org.danteplanner.moduleboundaryfixture.backend.moderation.controller;

import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.user.entity.User;

public record ModerationController(User user, PlannerRepository plannerRepository) {
}

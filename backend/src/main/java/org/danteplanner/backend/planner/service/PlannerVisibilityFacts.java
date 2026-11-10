package org.danteplanner.backend.planner.service;

import java.util.UUID;

public record PlannerVisibilityFacts(UUID plannerId, long ownerId, boolean published, boolean authorDeleted) {
}

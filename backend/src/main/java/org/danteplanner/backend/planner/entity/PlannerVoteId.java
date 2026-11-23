package org.danteplanner.backend.planner.entity;

import java.io.Serializable;
import java.util.UUID;

public record PlannerVoteId(Long userId, UUID plannerId) implements Serializable {
}

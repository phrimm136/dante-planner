package org.danteplanner.backend.planner.entity;

import java.io.Serializable;
import java.util.UUID;

public record PlannerSubscriptionId(Long userId, UUID plannerId) implements Serializable {
}

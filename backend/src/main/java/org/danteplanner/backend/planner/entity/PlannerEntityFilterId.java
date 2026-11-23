package org.danteplanner.backend.planner.entity;

import java.io.Serializable;
import java.util.UUID;

import org.danteplanner.backend.shared.entity.ContentEntityType;

public record PlannerEntityFilterId(ContentEntityType entityType, Integer entityId, UUID plannerId) implements Serializable {
}

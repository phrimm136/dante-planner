package org.danteplanner.backend.planner.entity;

import java.io.Serializable;
import java.util.UUID;

public record PlannerKeywordFilterId(String keyword, UUID plannerId) implements Serializable {
}

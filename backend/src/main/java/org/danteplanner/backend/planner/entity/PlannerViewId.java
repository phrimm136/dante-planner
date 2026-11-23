package org.danteplanner.backend.planner.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

public record PlannerViewId(UUID plannerId, String viewerHash, LocalDate viewDate) implements Serializable {
}

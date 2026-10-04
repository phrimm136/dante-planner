package org.danteplanner.backend.moderation.api;

import java.util.UUID;

public interface PlannerReports {

    void createReport(Long userId, UUID plannerId);

    boolean hasReported(Long userId, UUID plannerId);
}

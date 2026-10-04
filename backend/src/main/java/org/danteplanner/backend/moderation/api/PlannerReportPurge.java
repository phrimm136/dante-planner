package org.danteplanner.backend.moderation.api;

import java.util.List;
import java.util.UUID;

public interface PlannerReportPurge {

    void deleteReportsFor(List<UUID> plannerIds);
}

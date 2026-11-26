package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.danteplanner.backend.planner.entity.PlannerPublication;

public interface PlannerAggregateInserts {

    void insertSatellites(PlannerContent content, PlannerPublication publication, PlannerModeration moderation);
}

package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerContent;

public interface PlannerAggregateInserts {

    void insertSatellites(PlannerContent content);
}

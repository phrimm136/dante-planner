package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerBookmark;
import org.danteplanner.backend.planner.entity.PlannerBookmarkId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlannerBookmarkRepository extends JpaRepository<PlannerBookmark, PlannerBookmarkId> {

    boolean existsByUserIdAndPlannerId(Long userId, UUID plannerId);

    long countByPlannerId(UUID plannerId);

    List<PlannerBookmark> findByUserIdAndPlannerIdIn(Long userId, List<UUID> plannerIds);
}

package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerSubscription;
import org.danteplanner.backend.planner.entity.PlannerSubscriptionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlannerSubscriptionRepository extends JpaRepository<PlannerSubscription, PlannerSubscriptionId> {

    Optional<PlannerSubscription> findByUserIdAndPlannerId(Long userId, UUID plannerId);

    List<PlannerSubscription> findByPlannerIdAndEnabledTrue(UUID plannerId);

    boolean existsByUserIdAndPlannerId(Long userId, UUID plannerId);

    /**
     * The key is the (user, planner) pair the caller supplies, so no id-null guard can tell a
     * new row from an existing one: passing a row that already exists overwrites it.
     */
    default PlannerSubscription insert(PlannerSubscription subscription) {
        return save(subscription);
    }
}

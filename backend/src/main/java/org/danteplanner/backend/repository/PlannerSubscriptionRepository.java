package org.danteplanner.backend.repository;

import org.danteplanner.backend.entity.PlannerSubscription;
import org.danteplanner.backend.entity.PlannerSubscriptionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for planner subscription operations.
 * Uses composite key (userId, plannerId) via PlannerSubscriptionId.
 */
@Repository
public interface PlannerSubscriptionRepository extends JpaRepository<PlannerSubscription, PlannerSubscriptionId> {

    /**
     * Find a subscription by user ID and planner ID.
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     * @return the subscription if exists
     */
    Optional<PlannerSubscription> findByUserIdAndPlannerId(Long userId, UUID plannerId);

    /**
     * Find all active (enabled) subscriptions for a planner.
     * Used for sending notifications to subscribers.
     *
     * @param plannerId the planner ID
     * @return list of active subscriptions
     */
    List<PlannerSubscription> findByPlannerIdAndEnabledTrue(UUID plannerId);

    /**
     * Check if a subscription exists for a user and planner.
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     * @return true if subscription exists
     */
    boolean existsByUserIdAndPlannerId(Long userId, UUID plannerId);
}

package org.danteplanner.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.entity.PlannerSubscription;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.PlannerSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing planner subscriptions.
 * Handles subscription toggle, status checks, and subscriber retrieval.
 */
@Service
@Slf4j
public class PlannerSubscriptionService {

    private final PlannerSubscriptionRepository subscriptionRepository;
    private final PlannerRepository plannerRepository;

    public PlannerSubscriptionService(
            PlannerSubscriptionRepository subscriptionRepository,
            PlannerRepository plannerRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.plannerRepository = plannerRepository;
    }

    /**
     * Toggle subscription state for a user on a planner.
     * Creates new subscription if not exists, toggles enabled state if exists.
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     * @return the subscription with updated state
     * @throws PlannerNotFoundException if planner not found or not published
     */
    @Transactional
    public PlannerSubscription toggleSubscription(Long userId, UUID plannerId) {
        // Verify planner exists and is published
        if (plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId).isEmpty()) {
            throw new PlannerNotFoundException(plannerId);
        }

        var existingSubscription = subscriptionRepository.findByUserIdAndPlannerId(userId, plannerId);

        if (existingSubscription.isPresent()) {
            PlannerSubscription subscription = existingSubscription.get();
            subscription.toggle();
            PlannerSubscription saved = subscriptionRepository.save(subscription);
            log.debug("User {} toggled subscription for planner {} to enabled={}",
                    userId, plannerId, saved.isEnabled());
            return saved;
        } else {
            PlannerSubscription subscription = new PlannerSubscription(userId, plannerId);
            PlannerSubscription saved = subscriptionRepository.save(subscription);
            log.debug("User {} created subscription for planner {}", userId, plannerId);
            return saved;
        }
    }

    /**
     * Check if a user is subscribed (and enabled) to a planner.
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     * @return true if subscribed and enabled, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isSubscribed(Long userId, UUID plannerId) {
        return subscriptionRepository.findByUserIdAndPlannerId(userId, plannerId)
                .map(PlannerSubscription::isEnabled)
                .orElse(false);
    }

    /**
     * Create a subscription for a user on a planner.
     * Used for auto-subscribing owner when publishing.
     * No-op if subscription already exists.
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     */
    @Transactional
    public void createSubscription(Long userId, UUID plannerId) {
        if (subscriptionRepository.existsByUserIdAndPlannerId(userId, plannerId)) {
            log.debug("Subscription already exists for user {} on planner {}", userId, plannerId);
            return;
        }

        PlannerSubscription subscription = new PlannerSubscription(userId, plannerId);
        subscriptionRepository.save(subscription);
        log.debug("Auto-created subscription for user {} on planner {}", userId, plannerId);
    }

    /**
     * Get all user IDs subscribed (and enabled) to a planner.
     * Used by notification service to send notifications.
     *
     * @param plannerId the planner ID
     * @return list of user IDs with enabled subscriptions
     */
    @Transactional(readOnly = true)
    public List<Long> getSubscriberUserIds(UUID plannerId) {
        return subscriptionRepository.findByPlannerIdAndEnabledTrue(plannerId)
                .stream()
                .map(PlannerSubscription::getUserId)
                .toList();
    }
}

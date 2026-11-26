package org.danteplanner.backend.planner.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.api.RestrictionCheck;
import org.danteplanner.backend.planner.repository.PlannerSubscriptionRepository;
import org.danteplanner.backend.shared.failure.CommandOutcome;
import org.danteplanner.backend.shared.failure.WriteOutcome;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.restriction.PrivateWrite;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing planner subscriptions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerSubscriptionService {

    private final PlannerSubscriptionRepository subscriptionRepository;
    private final PlannerAccessGuard accessGuard;
    private final RestrictionCheck restrictionCheck;
    private final Clock clock;

    private enum SubscriptionState {
        OFF,
        ON;

        static SubscriptionState of(Optional<Boolean> enabled) {
            return enabled.orElse(false) ? ON : OFF;
        }
    }

    private enum ToggleOutcome implements CommandOutcome {
        CREATED,
        TOGGLED;

        static ToggleOutcome of(int affectedRows) {
            return switch (affectedRows) {
                case 1 -> CREATED;
                default -> TOGGLED;
            };
        }
    }

    @Transactional
    public boolean toggleSubscription(Long userId, UUID plannerId) {
        accessGuard.checkPublished(plannerId);

        ToggleOutcome outcome = ToggleOutcome.of(subscriptionRepository.toggle(userId, plannerId, clock.instant()));
        SubscriptionState entered = SubscriptionState.of(subscriptionRepository.findEnabledOf(userId, plannerId));
        switch (entered) {
            case ON -> restrictionCheck.record(restrictionCheck.requireUnrestricted(userId));
            case OFF -> restrictionCheck.record(new PrivateWrite());
        }
        FreshnessDeclarations.declare(new FreshnessScope.Planner(plannerId));
        boolean enabled = entered == SubscriptionState.ON;
        switch (outcome) {
            case CREATED -> log.debug("User {} created subscription for planner {}", userId, plannerId);
            case TOGGLED -> log.debug("User {} toggled subscription for planner {} to enabled={}",
                    userId, plannerId, enabled);
        }
        return enabled;
    }

    @Transactional
    public void subscribe(Long userId, UUID plannerId) {
        accessGuard.checkPublished(plannerId);

        subscriptionRepository.subscribe(userId, plannerId, clock.instant());
        FreshnessDeclarations.declare(new FreshnessScope.Planner(plannerId));
        log.debug("User {} subscribed to planner {}", userId, plannerId);
    }

    @Transactional
    public void unsubscribe(Long userId, UUID plannerId) {
        accessGuard.checkPublished(plannerId);

        subscriptionRepository.unsubscribe(userId, plannerId);
        FreshnessDeclarations.declare(new FreshnessScope.Planner(plannerId));
        log.debug("User {} unsubscribed from planner {}", userId, plannerId);
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public boolean isSubscribed(Long userId, UUID plannerId) {
        return subscriptionRepository.findEnabledOf(userId, plannerId).orElse(false);
    }

    @Transactional
    public void createSubscription(Long userId, UUID plannerId) {
        switch (WriteOutcome.of(subscriptionRepository.insertIfAbsent(userId, plannerId, clock.instant()))) {
            case APPLIED -> log.debug("Auto-created subscription for user {} on planner {}", userId, plannerId);
            case NOTHING -> log.debug("Subscription already exists for user {} on planner {}", userId, plannerId);
        }
    }
}

package org.danteplanner.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a user's subscription to a planner.
 * Uses composite key (userId, plannerId) to ensure one subscription per user per planner.
 *
 * Implements Persistable to handle composite key persistence correctly.
 * JPA's save() uses merge() for entities with composite keys where IDs are set,
 * which doesn't insert new entities properly without this interface.
 */
@Entity
@Table(name = "planner_subscriptions")
@IdClass(PlannerSubscriptionId.class)
public class PlannerSubscription implements Persistable<PlannerSubscriptionId> {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "planner_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID plannerId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    /**
     * No-arg constructor for JPA.
     */
    protected PlannerSubscription() {
    }

    public PlannerSubscription(Long userId, UUID plannerId) {
        this.userId = userId;
        this.plannerId = plannerId;
        this.enabled = true;
        this.isNew = true;
    }

    @Override
    public PlannerSubscriptionId getId() {
        return new PlannerSubscriptionId(userId, plannerId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PostPersist
    @PostLoad
    protected void markNotNew() {
        this.isNew = false;
    }

    // Business methods

    /**
     * Toggle the enabled state of this subscription.
     */
    public void toggle() {
        this.enabled = !this.enabled;
    }

    // Getters

    public Long getUserId() {
        return userId;
    }

    public UUID getPlannerId() {
        return plannerId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

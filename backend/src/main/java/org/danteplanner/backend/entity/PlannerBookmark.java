package org.danteplanner.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a user's bookmark on a planner.
 * Uses composite key (userId, plannerId) to ensure one bookmark per user per planner.
 *
 * Implements Persistable to handle composite key persistence correctly.
 * JPA's save() uses merge() for entities with composite keys where IDs are set,
 * which doesn't insert new entities properly without this interface.
 */
@Entity
@Table(name = "planner_bookmarks")
@IdClass(PlannerBookmarkId.class)
public class PlannerBookmark implements Persistable<PlannerBookmarkId> {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "planner_id", columnDefinition = "CHAR(36)", nullable = false)
    private UUID plannerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    public PlannerBookmark() {
    }

    public PlannerBookmark(Long userId, UUID plannerId) {
        this.userId = userId;
        this.plannerId = plannerId;
        this.isNew = true;
    }

    @Override
    public PlannerBookmarkId getId() {
        return new PlannerBookmarkId(userId, plannerId);
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

    // Getters and Setters

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public UUID getPlannerId() {
        return plannerId;
    }

    public void setPlannerId(UUID plannerId) {
        this.plannerId = plannerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

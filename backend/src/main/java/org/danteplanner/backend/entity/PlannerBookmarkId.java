package org.danteplanner.backend.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for PlannerBookmark entity.
 * Combines userId and plannerId to ensure one bookmark per user per planner.
 */
public class PlannerBookmarkId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private UUID plannerId;

    public PlannerBookmarkId() {
    }

    public PlannerBookmarkId(Long userId, UUID plannerId) {
        this.userId = userId;
        this.plannerId = plannerId;
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlannerBookmarkId that = (PlannerBookmarkId) o;
        return Objects.equals(userId, that.userId) &&
               Objects.equals(plannerId, that.plannerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, plannerId);
    }
}

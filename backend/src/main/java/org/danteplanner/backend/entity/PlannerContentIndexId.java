package org.danteplanner.backend.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for PlannerContentIndex entity.
 * Combines entityType, entityId, and plannerId to index planner content for search.
 */
public class PlannerContentIndexId implements Serializable {

    private static final long serialVersionUID = 1L;

    private ContentEntityType entityType;
    private String entityId;
    private UUID plannerId;

    public PlannerContentIndexId() {
    }

    public PlannerContentIndexId(ContentEntityType entityType, String entityId, UUID plannerId) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.plannerId = plannerId;
    }

    public ContentEntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(ContentEntityType entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
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
        PlannerContentIndexId that = (PlannerContentIndexId) o;
        return entityType == that.entityType &&
               Objects.equals(entityId, that.entityId) &&
               Objects.equals(plannerId, that.plannerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityType, entityId, plannerId);
    }
}

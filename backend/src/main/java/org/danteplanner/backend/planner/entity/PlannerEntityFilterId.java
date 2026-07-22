package org.danteplanner.backend.planner.entity;

import org.danteplanner.backend.shared.entity.ContentEntityType;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for PlannerEntityFilter: (entityType, entityId, plannerId).
 */
public class PlannerEntityFilterId implements Serializable {

    private static final long serialVersionUID = 1L;

    private ContentEntityType entityType;
    private Integer entityId;
    private UUID plannerId;

    public PlannerEntityFilterId() {
    }

    public PlannerEntityFilterId(ContentEntityType entityType, Integer entityId, UUID plannerId) {
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

    public Integer getEntityId() {
        return entityId;
    }

    public void setEntityId(Integer entityId) {
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
        PlannerEntityFilterId that = (PlannerEntityFilterId) o;
        return entityType == that.entityType &&
               Objects.equals(entityId, that.entityId) &&
               Objects.equals(plannerId, that.plannerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityType, entityId, plannerId);
    }
}

package org.danteplanner.backend.planner.entity;

import org.danteplanner.backend.shared.entity.ContentEntityType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Inverted index from a content entity (identity, EGO, gift, theme pack) to the
 * published planners containing it. All entity ids are integers.
 *
 * Implements Persistable to handle composite key persistence correctly.
 * JPA's save() uses merge() for entities with composite keys where IDs are set,
 * which doesn't insert new entities properly without this interface.
 */
@Entity
@Table(name = "planner_entity_filter")
@IdClass(PlannerEntityFilterId.class)
public class PlannerEntityFilter implements Persistable<PlannerEntityFilterId> {

    @Id
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "entity_type", nullable = false)
    private ContentEntityType entityType;

    @Id
    @Column(name = "entity_id", nullable = false)
    private Integer entityId;

    @Id
    @Column(name = "planner_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID plannerId;

    @Transient
    private boolean isNew = true;

    public PlannerEntityFilter() {
    }

    public PlannerEntityFilter(ContentEntityType entityType, Integer entityId, UUID plannerId) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.plannerId = plannerId;
        this.isNew = true;
    }

    @Override
    public PlannerEntityFilterId getId() {
        return new PlannerEntityFilterId(entityType, entityId, plannerId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    protected void markNotNew() {
        this.isNew = false;
    }

    public ContentEntityType getEntityType() {
        return entityType;
    }

    public Integer getEntityId() {
        return entityId;
    }

    public UUID getPlannerId() {
        return plannerId;
    }
}

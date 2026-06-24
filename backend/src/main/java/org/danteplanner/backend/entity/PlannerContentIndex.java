package org.danteplanner.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Entity representing an indexed content entry for a planner.
 * Used for searching published plans by their contained entities (identities, EGOs, gifts, theme packs).
 *
 * Implements Persistable to handle composite key persistence correctly.
 * JPA's save() uses merge() for entities with composite keys where IDs are set,
 * which doesn't insert new entities properly without this interface.
 */
@Entity
@Table(name = "planner_content_index")
@IdClass(PlannerContentIndexId.class)
public class PlannerContentIndex implements Persistable<PlannerContentIndexId> {

    @Id
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "entity_type", nullable = false)
    private ContentEntityType entityType;

    @Id
    @Column(name = "entity_id", length = 20, nullable = false)
    private String entityId;

    @Id
    @Column(name = "planner_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID plannerId;

    @Transient
    private boolean isNew = true;

    public PlannerContentIndex() {
    }

    public PlannerContentIndex(ContentEntityType entityType, String entityId, UUID plannerId) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.plannerId = plannerId;
        this.isNew = true;
    }

    @Override
    public PlannerContentIndexId getId() {
        return new PlannerContentIndexId(entityType, entityId, plannerId);
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

    public String getEntityId() {
        return entityId;
    }

    public UUID getPlannerId() {
        return plannerId;
    }
}

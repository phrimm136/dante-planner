package org.danteplanner.backend.planner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "planner")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Planner implements Persistable<UUID> {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "planner_type", nullable = false, updatable = false)
    private PlannerType plannerType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Planner(UUID id, Long userId, PlannerType plannerType, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.plannerType = plannerType;
        this.createdAt = createdAt;
    }

    public static Planner created(UUID id, Long userId, PlannerType plannerType, Instant createdAt) {
        return new Planner(id, userId, plannerType, createdAt);
    }

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    protected void markNotNew() {
        this.isNew = false;
    }
}

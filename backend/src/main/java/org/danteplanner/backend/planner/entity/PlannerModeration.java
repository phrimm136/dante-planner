package org.danteplanner.backend.planner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A row with {@code hiddenAt} set but a NULL {@code hiddenByModeratorId} means the
 * moderator account was deleted (FK ON DELETE SET NULL).
 */
@Entity
@Immutable
@Table(name = "planner_moderation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlannerModeration implements Persistable<UUID> {

    @Id
    @Column(name = "planner_id", columnDefinition = "BINARY(16)")
    private UUID plannerId;

    @Column(name = "taken_down_at")
    private @Nullable Instant takenDownAt;

    @Column(name = "hidden_from_recommended", nullable = false)
    private boolean hiddenFromRecommended;

    @Column(name = "hidden_by_moderator_id")
    private @Nullable Long hiddenByModeratorId;

    @Column(name = "hidden_reason", columnDefinition = "TEXT")
    private @Nullable String hiddenReason;

    @Column(name = "hidden_at")
    private @Nullable Instant hiddenAt;

    private PlannerModeration(UUID plannerId) {
        this.plannerId = plannerId;
    }

    public static PlannerModeration clear(UUID plannerId) {
        return new PlannerModeration(plannerId);
    }

    @Transient
    private boolean isNew = true;

    @Override
    public UUID getId() {
        return plannerId;
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
}

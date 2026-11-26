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

@Entity
@Immutable
@Table(name = "planner_publication")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlannerPublication implements Persistable<UUID> {

    @Id
    @Column(name = "planner_id", columnDefinition = "BINARY(16)")
    private UUID plannerId;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "first_published_at")
    private @Nullable Instant firstPublishedAt;

    @Column(name = "owner_notifications_enabled", nullable = false)
    private boolean ownerNotificationsEnabled;

    PlannerPublication(UUID plannerId, boolean published, @Nullable Instant firstPublishedAt,
            boolean ownerNotificationsEnabled) {
        this.plannerId = plannerId;
        this.published = published;
        this.firstPublishedAt = firstPublishedAt;
        this.ownerNotificationsEnabled = ownerNotificationsEnabled;
    }

    public static PlannerPublication draft(UUID plannerId) {
        return new PlannerPublication(plannerId, false, null, true);
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

package org.danteplanner.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entity representing a unique view of a planner.
 * Uses composite key (plannerId, viewerHash, viewDate) to ensure one view per viewer per day.
 *
 * Implements Persistable to handle composite key persistence correctly.
 * JPA's save() uses merge() for entities with composite keys where IDs are set,
 * which doesn't insert new entities properly without this interface.
 */
@Entity
@Table(name = "planner_views")
@IdClass(PlannerViewId.class)
public class PlannerView implements Persistable<PlannerViewId> {

    @Id
    @Column(name = "planner_id", columnDefinition = "CHAR(36)", nullable = false)
    private UUID plannerId;

    @Id
    @Column(name = "viewer_hash", length = 64, nullable = false)
    private String viewerHash;

    @Id
    @Column(name = "view_date", nullable = false)
    private LocalDate viewDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    public PlannerView() {
    }

    public PlannerView(UUID plannerId, String viewerHash, LocalDate viewDate) {
        this.plannerId = plannerId;
        this.viewerHash = viewerHash;
        this.viewDate = viewDate;
        this.isNew = true;
    }

    @Override
    public PlannerViewId getId() {
        return new PlannerViewId(plannerId, viewerHash, viewDate);
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

    public UUID getPlannerId() {
        return plannerId;
    }

    public void setPlannerId(UUID plannerId) {
        this.plannerId = plannerId;
    }

    public String getViewerHash() {
        return viewerHash;
    }

    public void setViewerHash(String viewerHash) {
        this.viewerHash = viewerHash;
    }

    public LocalDate getViewDate() {
        return viewDate;
    }

    public void setViewDate(LocalDate viewDate) {
        this.viewDate = viewDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

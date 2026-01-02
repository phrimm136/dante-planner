package org.danteplanner.backend.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for PlannerView entity.
 * Combines plannerId, viewerHash, and viewDate to ensure one view per viewer per day per planner.
 */
public class PlannerViewId implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID plannerId;
    private String viewerHash;
    private LocalDate viewDate;

    public PlannerViewId() {
    }

    public PlannerViewId(UUID plannerId, String viewerHash, LocalDate viewDate) {
        this.plannerId = plannerId;
        this.viewerHash = viewerHash;
        this.viewDate = viewDate;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlannerViewId that = (PlannerViewId) o;
        return Objects.equals(plannerId, that.plannerId) &&
               Objects.equals(viewerHash, that.viewerHash) &&
               Objects.equals(viewDate, that.viewDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plannerId, viewerHash, viewDate);
    }
}

package org.danteplanner.backend.planner.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for PlannerKeywordFilter: (keyword, plannerId).
 */
public class PlannerKeywordFilterId implements Serializable {

    private static final long serialVersionUID = 1L;

    private String keyword;
    private UUID plannerId;

    public PlannerKeywordFilterId() {
    }

    public PlannerKeywordFilterId(String keyword, UUID plannerId) {
        this.keyword = keyword;
        this.plannerId = plannerId;
    }

    public String getKeyword() {
        return keyword;
    }

    public UUID getPlannerId() {
        return plannerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlannerKeywordFilterId that = (PlannerKeywordFilterId) o;
        return Objects.equals(keyword, that.keyword) &&
               Objects.equals(plannerId, that.plannerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyword, plannerId);
    }
}

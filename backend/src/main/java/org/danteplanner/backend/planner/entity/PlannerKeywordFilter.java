package org.danteplanner.backend.planner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * Inverted index from a keyword slug to the published planners carrying it.
 * Keyword facet filtering reads this table; the JSON keyword array on the
 * content/catalog rows is display-only.
 *
 * Implements Persistable to handle composite key persistence correctly.
 * JPA's save() uses merge() for entities with composite keys where IDs are set,
 * which doesn't insert new entities properly without this interface.
 */
@Entity
@Table(name = "planner_keyword_filter")
@IdClass(PlannerKeywordFilterId.class)
public class PlannerKeywordFilter implements Persistable<PlannerKeywordFilterId> {

    @Id
    @Column(nullable = false, length = 64)
    private String keyword;

    @Id
    @Column(name = "planner_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID plannerId;

    @Transient
    private boolean isNew = true;

    public PlannerKeywordFilter() {
    }

    public PlannerKeywordFilter(String keyword, UUID plannerId) {
        this.keyword = keyword;
        this.plannerId = plannerId;
        this.isNew = true;
    }

    @Override
    public PlannerKeywordFilterId getId() {
        return new PlannerKeywordFilterId(keyword, plannerId);
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

    public String getKeyword() {
        return keyword;
    }

    public UUID getPlannerId() {
        return plannerId;
    }
}

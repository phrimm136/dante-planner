package org.danteplanner.backend.planner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Authoritative planner counters. Every viewer/voter/commenter write lands here as an
 * atomic increment — never load-mutate-save — so counter traffic cannot contend with
 * the owner's content row. Carries the recommended-notification CAS stamp because the
 * vote path that crosses the threshold is the only writer interested in it.
 */
@Entity
@Table(name = "planner_stats")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerStats implements Persistable<UUID> {

    @Id
    @Column(name = "planner_id", columnDefinition = "BINARY(16)")
    private UUID plannerId;

    @Transient
    @Builder.Default
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

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(nullable = false)
    private int upvotes;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "recommended_notified_at")
    private Instant recommendedNotifiedAt;
}

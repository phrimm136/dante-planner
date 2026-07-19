package org.danteplanner.backend.planner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Cutover read model for planner counters, written alongside the legacy {@code planners} columns
 * and served in their place once {@code planner.stats.reads-enabled} is on.
 */
@Entity
@Table(name = "planner_stats")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerStats {

    @Id
    @Column(name = "planner_id", columnDefinition = "BINARY(16)")
    private UUID plannerId;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(nullable = false)
    private int upvotes;
}

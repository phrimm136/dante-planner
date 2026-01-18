package org.danteplanner.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a user's report on a planner.
 * Reports are immutable once created - no business methods needed.
 * One report per user per planner (enforced at database level).
 */
@Entity
@Table(name = "planner_reports",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_planner_report_user_planner", columnNames = {"user_id", "planner_id"})
       })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlannerReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "planner_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID plannerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PlannerReport(Long userId, UUID plannerId) {
        this.userId = userId;
        this.plannerId = plannerId;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

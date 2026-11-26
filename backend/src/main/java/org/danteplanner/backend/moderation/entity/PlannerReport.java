package org.danteplanner.backend.moderation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedDate;

import org.danteplanner.backend.shared.entity.AuditedTimestampListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@EntityListeners(AuditedTimestampListener.class)
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

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PlannerReport(Long userId, UUID plannerId) {
        this.userId = userId;
        this.plannerId = plannerId;
    }
}

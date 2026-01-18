package org.danteplanner.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity representing a user's report on a comment.
 * Reports are immutable once created - no business methods needed.
 * One report per user per comment (enforced at database level).
 */
@Entity
@Table(name = "planner_comment_reports",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_comment_report_reporter_comment", columnNames = {"reporter_id", "comment_id"})
       })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlannerCommentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(name = "reason", nullable = false, length = 50)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PlannerCommentReport(Long commentId, Long reporterId, String reason) {
        this.commentId = commentId;
        this.reporterId = reporterId;
        this.reason = reason;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

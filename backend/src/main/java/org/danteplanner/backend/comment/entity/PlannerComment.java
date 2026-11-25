package org.danteplanner.backend.comment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.danteplanner.backend.shared.entity.AuditedTimestampListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@EntityListeners(AuditedTimestampListener.class)
@Table(name = "planner_comments",
       indexes = {
           @Index(name = "idx_comment_user", columnList = "user_id"),
           @Index(name = "idx_comment_parent", columnList = "parent_comment_id")
       })
public class PlannerComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", columnDefinition = "BINARY(16)", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "planner_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID plannerId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "parent_comment_id")
    private @Nullable Long parentCommentId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.TINYINT)
    private int depth;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PlannerComment() {
    }

    public PlannerComment(UUID plannerId, Long userId, String content, @Nullable Long parentCommentId, int depth) {
        this.plannerId = plannerId;
        this.userId = userId;
        this.content = content;
        this.parentCommentId = parentCommentId;
        this.depth = depth;
    }

    @PrePersist
    protected void onCreate() {
        publicId = UUID.randomUUID();
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public UUID getPlannerId() {
        return plannerId;
    }

    public Long getUserId() {
        return userId;
    }

    public @Nullable Long getParentCommentId() {
        return parentCommentId;
    }

    public String getContent() {
        return content;
    }

    public int getDepth() {
        return depth;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

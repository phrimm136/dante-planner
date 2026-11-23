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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.danteplanner.backend.shared.entity.AuditedTimestampListener;
import org.danteplanner.backend.shared.util.CommentConstants;

import java.time.Instant;
import java.util.UUID;

@Entity
@EntityListeners(AuditedTimestampListener.class)
@Table(name = "planner_comments",
       indexes = {
           @Index(name = "idx_comment_planner", columnList = "planner_id, deleted_at"),
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

    @Column(name = "upvote_count", nullable = false)
    private int upvoteCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private @Nullable Instant editedAt;

    @Column(name = "deleted_at")
    private @Nullable Instant deletedAt;

    @Column(name = "author_notifications_enabled", nullable = false)
    private boolean authorNotificationsEnabled = true;

    public PlannerComment() {
    }

    public PlannerComment(UUID plannerId, Long userId, String content, @Nullable Long parentCommentId, int depth) {
        this.plannerId = plannerId;
        this.userId = userId;
        this.content = content;
        this.parentCommentId = parentCommentId;
        this.depth = depth;
        this.upvoteCount = 0;
    }

    @PrePersist
    protected void onCreate() {
        publicId = UUID.randomUUID();
    }


    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.content = CommentConstants.DELETED_CONTENT;
    }

    public void edit(String newContent, Instant now) {
        this.content = newContent;
        this.editedAt = now;
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public void setPublicId(UUID publicId) {
        this.publicId = publicId;
    }

    public UUID getPlannerId() {
        return plannerId;
    }

    public void setPlannerId(UUID plannerId) {
        this.plannerId = plannerId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public @Nullable Long getParentCommentId() {
        return parentCommentId;
    }

    public void setParentCommentId(@Nullable Long parentCommentId) {
        this.parentCommentId = parentCommentId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public int getUpvoteCount() {
        return upvoteCount;
    }

    public void setUpvoteCount(int upvoteCount) {
        this.upvoteCount = upvoteCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public @Nullable Instant getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(@Nullable Instant editedAt) {
        this.editedAt = editedAt;
    }

    public @Nullable Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(@Nullable Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isAuthorNotificationsEnabled() {
        return authorNotificationsEnabled;
    }

    public void setAuthorNotificationsEnabled(boolean authorNotificationsEnabled) {
        this.authorNotificationsEnabled = authorNotificationsEnabled;
    }
}

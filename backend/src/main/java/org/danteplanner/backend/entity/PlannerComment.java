package org.danteplanner.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a comment on a planner.
 * Supports threaded replies with parent-child relationship and depth tracking.
 * Uses soft-delete pattern for comment deletion.
 */
@Entity
@Table(name = "planner_comments",
       indexes = {
           @Index(name = "idx_comment_planner", columnList = "planner_id, deleted_at"),
           @Index(name = "idx_comment_user", columnList = "user_id"),
           @Index(name = "idx_comment_parent", columnList = "parent_comment_id")
       })
public class PlannerComment {

    /** Unlimited depth - frontend handles visual flattening */
    public static final int MAX_DEPTH = Integer.MAX_VALUE;

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
    private Long parentCommentId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private int depth;

    @Column(name = "upvote_count", nullable = false)
    private int upvoteCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "author_notifications_enabled", nullable = false)
    private Boolean authorNotificationsEnabled = true;

    public PlannerComment() {
    }

    public PlannerComment(UUID plannerId, Long userId, String content, Long parentCommentId, int depth) {
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
        createdAt = Instant.now();
    }

    // Soft delete helpers

    /**
     * Check if this comment has been soft deleted.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Soft delete this comment.
     * Sets deletedAt timestamp and clears content for privacy.
     */
    public void softDelete() {
        this.deletedAt = Instant.now();
        this.content = "";
    }

    /**
     * Edit the comment content and track edit time.
     */
    public void edit(String newContent) {
        this.content = newContent;
        this.editedAt = Instant.now();
    }

    // Getters and Setters

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

    public Long getParentCommentId() {
        return parentCommentId;
    }

    public void setParentCommentId(Long parentCommentId) {
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

    public Instant getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(Instant editedAt) {
        this.editedAt = editedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Boolean getAuthorNotificationsEnabled() {
        return authorNotificationsEnabled;
    }

    public void setAuthorNotificationsEnabled(Boolean authorNotificationsEnabled) {
        this.authorNotificationsEnabled = authorNotificationsEnabled;
    }
}

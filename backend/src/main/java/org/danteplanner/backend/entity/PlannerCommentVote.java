package org.danteplanner.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * Entity representing a user's vote on a comment.
 * Uses composite key (commentId, userId) to ensure one vote per user per comment.
 *
 * Implements Persistable to handle composite key persistence correctly.
 * JPA's save() uses merge() for entities with composite keys where IDs are set,
 * which doesn't insert new entities properly without this interface.
 */
@Entity
@Table(name = "planner_comment_votes",
       indexes = {
           @Index(name = "idx_comment_vote_comment", columnList = "comment_id"),
           @Index(name = "idx_comment_vote_user", columnList = "user_id")
       })
@IdClass(PlannerCommentVoteId.class)
public class PlannerCommentVote implements Persistable<PlannerCommentVoteId> {

    @Id
    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_type", nullable = false)
    private CommentVoteType voteType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @Transient
    private boolean isNew = true;

    public PlannerCommentVote() {
    }

    public PlannerCommentVote(Long commentId, Long userId, CommentVoteType voteType) {
        this.commentId = commentId;
        this.userId = userId;
        this.voteType = voteType;
        this.isNew = true;
    }

    @Override
    public PlannerCommentVoteId getId() {
        return new PlannerCommentVoteId(commentId, userId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PostPersist
    @PostLoad
    protected void markNotNew() {
        this.isNew = false;
    }

    // Soft delete helpers

    /**
     * Check if this vote has been soft deleted.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Soft delete this vote.
     */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    /**
     * Reactivate a soft-deleted vote with a new vote type.
     * Service layer decides which vote type to use.
     */
    public void reactivate(CommentVoteType voteType) {
        this.deletedAt = null;
        this.updatedAt = Instant.now();
        this.voteType = voteType;
    }

    // Getters and Setters

    public Long getCommentId() {
        return commentId;
    }

    public void setCommentId(Long commentId) {
        this.commentId = commentId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public CommentVoteType getVoteType() {
        return voteType;
    }

    public void setVoteType(CommentVoteType voteType) {
        this.voteType = voteType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}

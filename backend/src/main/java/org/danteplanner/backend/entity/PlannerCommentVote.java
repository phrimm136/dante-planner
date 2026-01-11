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

    /**
     * User ID who cast the vote.
     * IMMUTABILITY EXCEPTION: This field is normally immutable, but can be updated
     * via {@link org.danteplanner.backend.repository.PlannerCommentVoteRepository#reassignUserVotes}
     * during user hard-delete to preserve vote counts while anonymizing the voter.
     * Never modify directly outside of reassignment operations.
     */
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_type", nullable = false)
    private final CommentVoteType voteType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    /**
     * No-arg constructor for JPA.
     * Sets voteType to null - should only be used by JPA for entity loading.
     */
    public PlannerCommentVote() {
        this.voteType = null;
    }

    public PlannerCommentVote(Long commentId, Long userId, CommentVoteType voteType) {
        this.commentId = commentId;
        this.userId = userId;
        this.voteType = voteType; // final field assignment
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
        // Validate entity integrity after loading from database
        if (this.voteType == null) {
            throw new IllegalStateException(
                "Comment vote loaded with null voteType - data corruption detected for vote: " + getId());
        }
    }

    // Getters only - votes are immutable

    public Long getCommentId() {
        return commentId;
    }

    public Long getUserId() {
        return userId;
    }

    public CommentVoteType getVoteType() {
        return voteType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

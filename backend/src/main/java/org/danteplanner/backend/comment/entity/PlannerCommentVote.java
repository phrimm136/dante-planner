package org.danteplanner.backend.comment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.util.Assert;

import java.time.Instant;

/**
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
    private final CommentVoteType voteType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    public PlannerCommentVote() {
        this.voteType = null;
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
        Assert.notNull(this.voteType,
                () -> "Comment vote loaded with null voteType - data corruption detected for vote: " + getId());
    }


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

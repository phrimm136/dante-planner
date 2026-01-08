package org.danteplanner.backend.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for PlannerCommentVote entity.
 * Combines commentId and userId to ensure one vote per user per comment.
 */
public class PlannerCommentVoteId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long commentId;
    private Long userId;

    public PlannerCommentVoteId() {
    }

    public PlannerCommentVoteId(Long commentId, Long userId) {
        this.commentId = commentId;
        this.userId = userId;
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlannerCommentVoteId that = (PlannerCommentVoteId) o;
        return Objects.equals(commentId, that.commentId) &&
               Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commentId, userId);
    }
}

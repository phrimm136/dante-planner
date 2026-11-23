package org.danteplanner.backend.comment.entity;

import java.io.Serializable;

public record PlannerCommentVoteId(Long commentId, Long userId) implements Serializable {
}

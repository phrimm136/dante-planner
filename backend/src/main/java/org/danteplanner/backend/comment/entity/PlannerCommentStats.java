package org.danteplanner.backend.comment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "planner_comment_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlannerCommentStats {

    @Id
    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "upvote_count", nullable = false)
    private int upvoteCount;
}

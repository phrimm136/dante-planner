package org.danteplanner.backend.comment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

@Entity
@Immutable
@Table(name = "planner_comment_votes",
       indexes = {
           @Index(name = "idx_comment_vote_comment", columnList = "comment_id"),
           @Index(name = "idx_comment_vote_user", columnList = "user_id")
       })
@IdClass(PlannerCommentVoteId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlannerCommentVote {

    @Id
    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_type", nullable = false)
    private CommentVoteType voteType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

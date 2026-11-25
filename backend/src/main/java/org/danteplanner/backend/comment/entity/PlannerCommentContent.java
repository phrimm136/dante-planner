package org.danteplanner.backend.comment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Entity
@Immutable
@Table(name = "planner_comment_content")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlannerCommentContent {

    @Id
    @Column(name = "comment_id")
    private Long commentId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "edited_at")
    private @Nullable Instant editedAt;

    @Column(name = "deleted_at")
    private @Nullable Instant deletedAt;

    @Column(name = "author_notifications_enabled", nullable = false)
    private boolean authorNotificationsEnabled;
}

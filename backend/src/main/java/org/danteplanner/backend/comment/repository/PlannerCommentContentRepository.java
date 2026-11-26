package org.danteplanner.backend.comment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.danteplanner.backend.comment.entity.PlannerCommentContent;

import java.time.Instant;

@Repository
public interface PlannerCommentContentRepository extends JpaRepository<PlannerCommentContent, Long> {

    @Modifying
    @Query(value = "INSERT INTO planner_comment_content (comment_id, content) VALUES (:commentId, :content)",
            nativeQuery = true)
    int insertContent(@Param("commentId") Long commentId, @Param("content") String content);

    @Modifying
    @Query("""
        UPDATE PlannerCommentContent t SET t.content = :content, t.editedAt = :editedAt
        WHERE t.commentId = :commentId AND t.deletedAt IS NULL
        """)
    int edit(@Param("commentId") Long commentId, @Param("content") String content,
            @Param("editedAt") Instant editedAt);

    @Modifying
    @Query("""
        UPDATE PlannerCommentContent t SET t.content = :deletedContent, t.deletedAt = :deletedAt
        WHERE t.commentId = :commentId AND t.deletedAt IS NULL
        """)
    int withdraw(@Param("commentId") Long commentId, @Param("deletedContent") String deletedContent,
            @Param("deletedAt") Instant deletedAt);

    @Modifying
    @Query("""
        UPDATE PlannerCommentContent t SET t.authorNotificationsEnabled = :enabled WHERE t.commentId = :commentId
        """)
    int setAuthorNotifications(@Param("commentId") Long commentId, @Param("enabled") boolean enabled);
}

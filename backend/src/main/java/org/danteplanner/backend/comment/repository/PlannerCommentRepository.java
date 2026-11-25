package org.danteplanner.backend.comment.repository;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlannerCommentRepository extends JpaRepository<PlannerComment, Long> {

    @Query(CommentRow.SELECT + "WHERE c.plannerId = :plannerId ORDER BY c.createdAt ASC")
    List<CommentRow> findByPlannerId(@Param("plannerId") UUID plannerId);

    @Query(CommentRow.SELECT + "WHERE c.publicId = :publicId")
    Optional<CommentRow> findByPublicId(@Param("publicId") UUID publicId);

    @Query(CommentRow.SELECT + "WHERE c.id = :commentId")
    Optional<CommentRow> findRowById(@Param("commentId") Long commentId);

    @Modifying
    @Query(value = "UPDATE planner_comments c "
            + "LEFT JOIN planner_comment_content t ON t.comment_id = c.id "
            + "SET c.user_id = :sentinelId, c.content = :deletedContent, t.content = :deletedContent "
            + "WHERE c.user_id = :userId",
            nativeQuery = true)
    int anonymizeCommentsToSentinel(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId,
            @Param("deletedContent") String deletedContent);

    default PlannerComment insert(PlannerComment comment) {
        Assert.isNull(comment.getId(), "insert() takes new rows only");
        return save(comment);
    }
}

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

    @Query("""
        SELECT c FROM PlannerComment c
        WHERE c.plannerId = :plannerId
        ORDER BY c.createdAt ASC
        """)
    List<PlannerComment> findByPlannerId(@Param("plannerId") UUID plannerId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PlannerComment c SET c.upvoteCount = c.upvoteCount + 1 WHERE c.id = :commentId")
    int incrementUpvoteCount(@Param("commentId") Long commentId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PlannerComment c SET c.upvoteCount = c.upvoteCount - 1 WHERE c.id = :commentId AND c.upvoteCount > 0")
    int decrementUpvoteCount(@Param("commentId") Long commentId);

    @Modifying
    @Query("UPDATE PlannerComment c SET c.userId = :sentinelId, c.content = '' WHERE c.userId = :userId")
    int anonymizeCommentsToSentinel(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId);

    long countByPlannerIdAndDeletedAtIsNull(UUID plannerId);

    Optional<PlannerComment> findByPublicId(UUID publicId);

    default PlannerComment insert(PlannerComment comment) {
        Assert.isNull(comment.getId(), "insert() takes new rows only");
        return save(comment);
    }
}

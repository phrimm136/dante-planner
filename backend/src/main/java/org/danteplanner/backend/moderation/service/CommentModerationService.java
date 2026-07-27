package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.exception.CommentNotFoundException;
import org.danteplanner.backend.comment.service.CommentCommandService;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Moderator deletion of any comment, regardless of authorship.
 *
 * <p>Deletion is a soft delete that keeps the thread structure intact, and it is idempotent: a
 * second delete of the same comment is not an error.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommentModerationService {

    private final CommentCommandService commentCommandService;
    private final CommentQueryService commentQueryService;
    private final ModerationAuditService auditService;

    /**
     * Soft-delete any comment as a moderator.
     *
     * @param actorId   the moderator/admin performing the action
     * @param commentId the comment ID to delete
     * @throws CommentNotFoundException if comment not found
     */
    @Transactional
    public void deleteComment(Long actorId, Long commentId) {
        PlannerComment comment = commentQueryService.requireById(commentId);

        if (comment.isDeleted()) {
            return;
        }

        commentCommandService.softDelete(comment);

        auditService.record(actorId, comment.getPublicId().toString(),
                ModerationAction.ActionType.DELETE_COMMENT, ModerationAction.TargetType.COMMENT, null, null);

        log.info("Moderator {} deleted comment {}", actorId, commentId);
    }

    /**
     * Delete a comment by public ID (UUID).
     *
     * <p>The audit record is written before the already-deleted check, so a repeated delete still
     * records that a moderator acted.</p>
     *
     * @param actorId         the moderator/admin user ID
     * @param commentPublicId the comment's public UUID
     * @param reason          reason for deletion (for audit trail)
     * @throws CommentNotFoundException if comment not found
     */
    @Transactional
    public void deleteCommentByPublicId(Long actorId, UUID commentPublicId, String reason) {
        PlannerComment comment = commentQueryService.requireByPublicId(commentPublicId);

        auditService.record(actorId, comment.getPublicId().toString(),
                ModerationAction.ActionType.DELETE_COMMENT, ModerationAction.TargetType.COMMENT, reason, null);

        if (comment.isDeleted()) {
            log.info("Moderator {} attempted delete of already-deleted comment {} (idempotent)", actorId, commentPublicId);
            return;
        }

        commentCommandService.softDelete(comment);

        log.info("Moderator {} deleted comment {} with reason: {}", actorId, commentPublicId, reason);
    }
}

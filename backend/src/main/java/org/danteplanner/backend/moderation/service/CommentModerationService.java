package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.service.CommentCommandService;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Moderator deletion of any comment, regardless of authorship.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommentModerationService {

    private final CommentCommandService commentCommandService;
    private final CommentQueryService commentQueryService;
    private final ModerationAuditService auditService;

    @Transactional
    public void deleteComment(Long actorId, Long commentId) {
        PlannerComment comment = commentQueryService.requireById(commentId);

        if (comment.isDeleted()) {
            return;
        }

        commentCommandService.softDelete(comment);

        auditService.record(actorId, comment.getPublicId().toString(),
                ModerationAction.ActionType.DELETE_COMMENT, ModerationAction.TargetType.COMMENT);

        log.info("Moderator {} deleted comment {}", actorId, commentId);
    }

    @Transactional
    public void deleteCommentByPublicId(Long actorId, UUID commentPublicId, String reason) {
        PlannerComment comment = commentQueryService.requireByPublicId(commentPublicId);

        auditService.record(actorId, comment.getPublicId().toString(),
                ModerationAction.ActionType.DELETE_COMMENT, ModerationAction.TargetType.COMMENT, reason);

        if (comment.isDeleted()) {
            log.info("Moderator {} attempted delete of already-deleted comment {} (idempotent)", actorId, commentPublicId);
            return;
        }

        commentCommandService.softDelete(comment);

        log.info("Moderator {} deleted comment {} with reason: {}", actorId, commentPublicId, reason);
    }
}

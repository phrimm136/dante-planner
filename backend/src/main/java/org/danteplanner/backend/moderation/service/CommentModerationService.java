package org.danteplanner.backend.moderation.service;

import org.danteplanner.backend.moderation.api.ModerationActionType;
import org.danteplanner.backend.moderation.api.ModerationTargetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.api.CommentModerationTarget;
import org.danteplanner.backend.moderation.api.ModeratedComment;
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

    private final CommentModerationTarget commentTarget;
    private final ModerationAuditService auditService;

    @Transactional
    public void deleteComment(Long actorId, Long commentId) {
        ModeratedComment comment = commentTarget.requireById(commentId);

        if (comment.deleted()) {
            return;
        }

        commentTarget.softDelete(commentId);

        auditService.record(actorId, comment.publicId().toString(),
                ModerationActionType.DELETE_COMMENT, ModerationTargetType.COMMENT);

        log.info("Moderator {} deleted comment {}", actorId, commentId);
    }

    @Transactional
    public void deleteCommentByPublicId(Long actorId, UUID commentPublicId, String reason) {
        ModeratedComment comment = commentTarget.requireByPublicId(commentPublicId);

        auditService.record(actorId, comment.publicId().toString(),
                ModerationActionType.DELETE_COMMENT, ModerationTargetType.COMMENT, reason);

        if (comment.deleted()) {
            log.info("Moderator {} attempted delete of already-deleted comment {} (idempotent)", actorId, commentPublicId);
            return;
        }

        commentTarget.softDelete(comment.id());

        log.info("Moderator {} deleted comment {} with reason: {}", actorId, commentPublicId, reason);
    }
}

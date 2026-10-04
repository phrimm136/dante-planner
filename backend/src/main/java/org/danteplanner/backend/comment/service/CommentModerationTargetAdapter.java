package org.danteplanner.backend.comment.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.validation.CommentStateValidator;
import org.danteplanner.backend.moderation.api.CommentModerationTarget;
import org.danteplanner.backend.moderation.api.ModeratedComment;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommentModerationTargetAdapter implements CommentModerationTarget {

    private final CommentQueryService commentQueryService;
    private final CommentCommandService commentCommandService;
    private final CommentStateValidator commentStateValidator;

    @Override
    public ModeratedComment requireById(Long commentId) {
        return moderated(commentQueryService.requireById(commentId));
    }

    @Override
    public ModeratedComment requireByPublicId(UUID commentPublicId) {
        return moderated(commentQueryService.requireByPublicId(commentPublicId));
    }

    @Override
    public void softDelete(Long commentId) {
        commentCommandService.softDelete(commentQueryService.requireById(commentId));
    }

    @Override
    public Long requireReportable(UUID commentPublicId) {
        PlannerComment comment = commentQueryService.requireByPublicId(commentPublicId);
        Long internalId = comment.getId();
        commentStateValidator.requireReportable(comment);
        return internalId;
    }

    private static ModeratedComment moderated(PlannerComment comment) {
        return new ModeratedComment(comment.getId(), comment.getPublicId(), comment.isDeleted());
    }
}

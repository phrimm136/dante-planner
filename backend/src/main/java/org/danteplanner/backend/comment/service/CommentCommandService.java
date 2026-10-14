package org.danteplanner.backend.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.comment.dto.CreateCommentRequest;
import org.danteplanner.backend.comment.dto.CreateCommentResponse;
import org.danteplanner.backend.comment.dto.UpdateCommentRequest;
import org.danteplanner.backend.comment.dto.UpdateCommentResponse;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.validation.CommentAccessValidator;
import org.danteplanner.backend.comment.validation.CommentAuthorshipValidator;
import org.danteplanner.backend.comment.validation.CommentStateValidator;
import org.danteplanner.backend.planner.api.PlannerAccess;
import org.danteplanner.backend.planner.api.PlannerCommentCounts;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import org.danteplanner.backend.shared.util.CommentConstants;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Writes comments: creation, editing, and withdrawal, each settling the planner's comment counter
 * in the same transaction as the row it counts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommentCommandService {

    private final PlannerCommentRepository commentRepository;
    private final CommentQueryService commentQueryService;
    private final DomainEventRecorder domainEventRecorder;
    private final PlannerAccess plannerAccess;
    private final PlannerCommentCounts plannerCommentCounts;
    private final CommentAccessValidator accessValidator;
    private final CommentAuthorshipValidator authorshipValidator;
    private final CommentStateValidator stateValidator;

    @Transactional
    public CreateCommentResponse createComment(UUID plannerId, Long userId, UUID deviceId, CreateCommentRequest request) {
        plannerAccess.checkPublished(plannerId);

        int depth = 0;
        Long effectiveParentId = null;

        if (request.parentCommentId() != null) {
            final UUID parentPublicId = request.parentCommentId();
            PlannerComment parent = commentQueryService.requireByPublicId(parentPublicId);
            effectiveParentId = parent.getId();

            accessValidator.requireParentInPlanner(parent, plannerId);
            stateValidator.requireReplyable(parent);

            depth = Math.min(parent.getDepth() + 1, CommentConstants.MAX_DEPTH);

            if (parent.getDepth() >= CommentConstants.MAX_DEPTH) {
                effectiveParentId = parent.getParentCommentId();
            }
        }

        PlannerComment comment = new PlannerComment(plannerId, userId, request.content(), effectiveParentId, depth);
        PlannerComment saved = commentRepository.insert(comment);
        plannerCommentCounts.incrementCommentCount(plannerId);

        if (effectiveParentId == null) {
            domainEventRecorder.recordDomainEvent(DomainEventType.COMMENT_RECEIVED, plannerId,
                    Map.of("commentId", saved.getId()));
        } else {
            domainEventRecorder.recordDomainEvent(DomainEventType.REPLY_RECEIVED, plannerId,
                    Map.of("replyId", saved.getId()));
        }

        log.info("User {} created comment {} on planner {}", userId, saved.getId(), plannerId);

        return new CreateCommentResponse(saved.getPublicId(), saved.getCreatedAt());
    }

    @Transactional
    public CreateCommentResponse createReply(UUID parentPublicId, Long userId, UUID deviceId, String content) {
        PlannerComment parent = commentQueryService.requireByPublicId(parentPublicId);

        UUID plannerId = parent.getPlannerId();

        plannerAccess.checkPublished(plannerId);

        stateValidator.requireReplyable(parent);

        int depth = Math.min(parent.getDepth() + 1, CommentConstants.MAX_DEPTH);

        Long effectiveParentId = parent.getId();
        if (parent.getDepth() >= CommentConstants.MAX_DEPTH) {
            effectiveParentId = parent.getParentCommentId();
        }

        PlannerComment reply = new PlannerComment(plannerId, userId, content, effectiveParentId, depth);
        PlannerComment saved = commentRepository.insert(reply);
        plannerCommentCounts.incrementCommentCount(plannerId);

        domainEventRecorder.recordDomainEvent(DomainEventType.REPLY_RECEIVED, plannerId,
                Map.of("replyId", saved.getId()));

        log.info("User {} created reply {} to comment {} on planner {}", userId, saved.getId(), parent.getId(), plannerId);

        return new CreateCommentResponse(saved.getPublicId(), saved.getCreatedAt());
    }

    @Transactional
    public UpdateCommentResponse updateComment(UUID commentPublicId, Long userId, UpdateCommentRequest request) {
        PlannerComment comment = commentQueryService.requireByPublicId(commentPublicId);

        stateValidator.requireEditable(comment);
        authorshipValidator.requireAuthorToEdit(comment, userId);

        comment.edit(request.content());
        log.info("User {} edited comment {}", userId, commentPublicId);

        return new UpdateCommentResponse(comment.getEditedAt());
    }

    @Transactional
    public void softDelete(PlannerComment comment) {
        // The withdrawal must precede the counter: decrementing detaches the comment, and a
        // mutation made after it would never reach the row.
        comment.softDelete();
        plannerCommentCounts.decrementCommentCount(comment.getPlannerId());
    }

    @Transactional
    public void deleteComment(UUID commentPublicId, Long userId) {
        PlannerComment comment = commentQueryService.requireByPublicId(commentPublicId);

        if (comment.isDeleted()) {
            return;
        }

        authorshipValidator.requireAuthorToDelete(comment, userId);

        softDelete(comment);
        log.info("User {} deleted comment {}", userId, commentPublicId);
    }

}

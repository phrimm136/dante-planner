package org.danteplanner.backend.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.comment.dto.CreateCommentRequest;
import org.danteplanner.backend.comment.dto.CreateCommentResponse;
import org.danteplanner.backend.comment.dto.UpdateCommentRequest;
import org.danteplanner.backend.comment.dto.UpdateCommentResponse;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.exception.CommentForbiddenException;
import org.danteplanner.backend.comment.exception.CommentNotFoundException;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.validation.CommentAccessValidator;
import org.danteplanner.backend.comment.validation.CommentAuthorshipValidator;
import org.danteplanner.backend.comment.validation.CommentStateValidator;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerStatsService;
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
    private final PlannerAccessGuard accessGuard;
    private final PlannerStatsService plannerStatsService;
    private final CommentAccessValidator accessValidator;
    private final CommentAuthorshipValidator authorshipValidator;
    private final CommentStateValidator stateValidator;

    /**
     * Create a new comment on a planner.
     *
     * @param plannerId the planner ID
     * @param userId    the user ID
     * @param deviceId  the device ID (for SSE broadcast exclusion)
     * @param request   the create request
     * @return the created comment response with id and timestamp
     * @throws PlannerNotFoundException if planner not found or not published
     * @throws CommentNotFoundException if parent comment not found
     * @throws CommentForbiddenException if replying to deleted top-level comment
     */
    @Transactional
    public CreateCommentResponse createComment(UUID plannerId, Long userId, UUID deviceId, CreateCommentRequest request) {
        accessGuard.checkNotRestricted(userId);

        accessGuard.checkPublished(plannerId);

        int depth = 0;
        Long effectiveParentId = null;

        // Handle reply logic - resolve UUID to internal ID
        if (request.parentCommentId() != null) {
            final UUID parentPublicId = request.parentCommentId();
            PlannerComment parent = commentQueryService.requireByPublicId(parentPublicId);
            effectiveParentId = parent.getId();

            accessValidator.requireParentInPlanner(parent, plannerId);
            stateValidator.requireReplyable(parent);

            // Calculate depth with max enforcement (flatten at max depth)
            depth = Math.min(parent.getDepth() + 1, CommentConstants.MAX_DEPTH);

            // If at max depth, replies become siblings instead of children
            if (parent.getDepth() >= CommentConstants.MAX_DEPTH) {
                effectiveParentId = parent.getParentCommentId();
            }
        }

        // Create and save comment
        PlannerComment comment = new PlannerComment(plannerId, userId, request.content(), effectiveParentId, depth);
        PlannerComment saved = commentRepository.insert(comment);
        plannerStatsService.incrementCommentCount(plannerId);

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

    /**
     * Create a reply to an existing comment.
     *
     * @param parentPublicId the parent comment's public UUID
     * @param userId         the user ID
     * @param deviceId       the device ID (for SSE broadcast exclusion)
     * @param content        the reply content
     * @return the created reply response with id and timestamp
     * @throws CommentNotFoundException if parent comment not found
     * @throws PlannerNotFoundException if parent's planner not found or not published
     * @throws CommentForbiddenException if replying to deleted top-level comment
     */
    @Transactional
    public CreateCommentResponse createReply(UUID parentPublicId, Long userId, UUID deviceId, String content) {
        accessGuard.checkNotRestricted(userId);

        PlannerComment parent = commentQueryService.requireByPublicId(parentPublicId);

        UUID plannerId = parent.getPlannerId();

        accessGuard.checkPublished(plannerId);

        stateValidator.requireReplyable(parent);

        // Calculate depth with max enforcement (flatten at max depth)
        int depth = Math.min(parent.getDepth() + 1, CommentConstants.MAX_DEPTH);

        // If at max depth, replies become siblings instead of children
        Long effectiveParentId = parent.getId();
        if (parent.getDepth() >= CommentConstants.MAX_DEPTH) {
            effectiveParentId = parent.getParentCommentId();
        }

        // Create and save reply
        PlannerComment reply = new PlannerComment(plannerId, userId, content, effectiveParentId, depth);
        PlannerComment saved = commentRepository.insert(reply);
        plannerStatsService.incrementCommentCount(plannerId);

        domainEventRecorder.recordDomainEvent(DomainEventType.REPLY_RECEIVED, plannerId,
                Map.of("replyId", saved.getId()));

        log.info("User {} created reply {} to comment {} on planner {}", userId, saved.getId(), parent.getId(), plannerId);

        return new CreateCommentResponse(saved.getPublicId(), saved.getCreatedAt());
    }

    /**
     * Update a comment's content.
     * Only the comment author can edit.
     *
     * @param commentPublicId the comment public UUID
     * @param userId          the user ID (must be author)
     * @param request         the update request
     * @return the edit timestamp
     * @throws CommentNotFoundException if comment not found
     * @throws CommentForbiddenException if user is not author or comment is deleted
     */
    @Transactional
    public UpdateCommentResponse updateComment(UUID commentPublicId, Long userId, UpdateCommentRequest request) {
        accessGuard.checkNotRestricted(userId);

        PlannerComment comment = commentQueryService.requireByPublicId(commentPublicId);

        stateValidator.requireEditable(comment);
        authorshipValidator.requireAuthorToEdit(comment, userId);

        comment.edit(request.content());
        log.info("User {} edited comment {}", userId, commentPublicId);

        return new UpdateCommentResponse(comment.getEditedAt());
    }

    /**
     * Withdraw a comment from view, keeping its row so the thread beneath it survives, and settle
     * the planner's comment counter in the same transaction.
     *
     * <p>Not idempotent on its own: a caller that may be re-entered has to check
     * {@link PlannerComment#isDeleted()} first, or the counter drops twice.</p>
     *
     * @param comment the comment to withdraw
     */
    @Transactional
    public void softDelete(PlannerComment comment) {
        // The withdrawal must precede the counter: decrementing detaches the comment, and a
        // mutation made after it would never reach the row.
        comment.softDelete();
        plannerStatsService.decrementCommentCount(comment.getPlannerId());
    }

    /**
     * Soft-delete a comment.
     * Only the comment author can delete their own comment.
     * Use CommentModerationService.deleteComment for moderator deletion.
     *
     * @param commentPublicId the comment public UUID
     * @param userId          the user ID (must be author)
     * @throws CommentNotFoundException if comment not found
     * @throws CommentForbiddenException if user is not author
     */
    @Transactional
    public void deleteComment(UUID commentPublicId, Long userId) {
        PlannerComment comment = commentQueryService.requireByPublicId(commentPublicId);

        // Already deleted - idempotent
        if (comment.isDeleted()) {
            return;
        }

        authorshipValidator.requireAuthorToDelete(comment, userId);

        softDelete(comment);
        log.info("User {} deleted comment {}", userId, commentPublicId);
    }

}

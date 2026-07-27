package org.danteplanner.backend.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.comment.dto.CommentTreeNode;
import org.danteplanner.backend.comment.dto.CreateCommentRequest;
import org.danteplanner.backend.comment.dto.CreateCommentResponse;
import org.danteplanner.backend.comment.dto.UpdateCommentRequest;
import org.danteplanner.backend.comment.dto.UpdateCommentResponse;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.exception.CommentForbiddenException;
import org.danteplanner.backend.comment.exception.CommentNotFoundException;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.notification.service.NotificationDispatchService;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerStatsService;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final UserService userService;
    private final NotificationDispatchService notificationDispatchService;
    private final SsePublisher ssePublisher;
    private final PlannerAccessGuard accessGuard;
    private final PlannerStatsService plannerStatsService;

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

        Planner planner = accessGuard.requirePublished(plannerId);

        int depth = 0;
        Long effectiveParentId = null;

        // Handle reply logic - resolve UUID to internal ID
        if (request.parentCommentId() != null) {
            final UUID parentPublicId = request.parentCommentId();
            PlannerComment parent = commentQueryService.requireByPublicId(parentPublicId);
            effectiveParentId = parent.getId();

            // Verify parent belongs to same planner
            if (!parent.getPlannerId().equals(plannerId)) {
                throw new CommentForbiddenException("Parent comment belongs to a different planner");
            }

            // Cannot reply to deleted TOP-LEVEL comments
            // But CAN reply to children of deleted comments (preserves thread continuity)
            if (parent.isDeleted() && parent.getDepth() == 0) {
                throw new CommentForbiddenException(effectiveParentId, "Cannot reply to deleted top-level comment");
            }

            // Calculate depth with max enforcement (flatten at max depth)
            depth = Math.min(parent.getDepth() + 1, PlannerComment.MAX_DEPTH);

            // If at max depth, replies become siblings instead of children
            if (parent.getDepth() >= PlannerComment.MAX_DEPTH) {
                effectiveParentId = parent.getParentCommentId();
            }
        }

        // Create and save comment
        PlannerComment comment = new PlannerComment(plannerId, userId, request.content(), effectiveParentId, depth);
        PlannerComment saved = commentRepository.save(comment);
        plannerStatsService.commentAdded(plannerId);
        log.info("User {} created comment {} on planner {}", userId, saved.getId(), plannerId);

        // Send notifications (respecting user notification settings)
        if (effectiveParentId == null) {
            // Top-level comment - notify planner owner (if not self-comment and owner has notifications enabled)
            Long plannerOwnerId = planner.getUser().getId();
            if (!userId.equals(plannerOwnerId) && Boolean.TRUE.equals(planner.getOwnerNotificationsEnabled())) {
                notificationDispatchService.notifyCommentReceived(
                        saved.getId(),
                        saved.getPublicId(),
                        plannerId,
                        planner.getTitle(),
                        request.content(),
                        plannerOwnerId,
                        userId
                );
                log.debug("Sent COMMENT_RECEIVED notification to planner owner {}", plannerOwnerId);
            }
        } else {
            // Reply - notify parent comment author (if not self-reply and author has notifications enabled)
            PlannerComment parentComment = commentRepository.findById(effectiveParentId).orElseThrow();
            Long parentAuthorId = parentComment.getUserId();
            if (!userId.equals(parentAuthorId) && Boolean.TRUE.equals(parentComment.getAuthorNotificationsEnabled())) {
                notificationDispatchService.notifyReplyReceived(
                        saved.getId(),
                        saved.getPublicId(),
                        plannerId,
                        planner.getTitle(),
                        request.content(),
                        parentAuthorId,
                        userId
                );
                log.debug("Sent REPLY_RECEIVED notification to parent author {}", parentAuthorId);
            }
        }

        publishCommentCreated(plannerId, saved, userId);

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

        Planner planner = accessGuard.requirePublished(plannerId);

        // Cannot reply to deleted TOP-LEVEL comments
        if (parent.isDeleted() && parent.getDepth() == 0) {
            throw new CommentForbiddenException(parent.getId(), "Cannot reply to deleted top-level comment");
        }

        // Calculate depth with max enforcement (flatten at max depth)
        int depth = Math.min(parent.getDepth() + 1, PlannerComment.MAX_DEPTH);

        // If at max depth, replies become siblings instead of children
        Long effectiveParentId = parent.getId();
        if (parent.getDepth() >= PlannerComment.MAX_DEPTH) {
            effectiveParentId = parent.getParentCommentId();
        }

        // Create and save reply
        PlannerComment reply = new PlannerComment(plannerId, userId, content, effectiveParentId, depth);
        PlannerComment saved = commentRepository.save(reply);
        plannerStatsService.commentAdded(plannerId);
        log.info("User {} created reply {} to comment {} on planner {}", userId, saved.getId(), parent.getId(), plannerId);

        // Send notification to parent author (if not self-reply and author has notifications enabled)
        PlannerComment notifyParent = commentRepository.findById(effectiveParentId).orElseThrow();
        Long parentAuthorId = notifyParent.getUserId();
        if (!userId.equals(parentAuthorId) && Boolean.TRUE.equals(notifyParent.getAuthorNotificationsEnabled())) {
            notificationDispatchService.notifyReplyReceived(
                    saved.getId(),
                    saved.getPublicId(),
                    plannerId,
                    planner.getTitle(),
                    content,
                    parentAuthorId,
                    userId
            );
            log.debug("Sent REPLY_RECEIVED notification to parent author {}", parentAuthorId);
        }

        publishCommentCreated(plannerId, saved, userId);

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

        // Cannot edit deleted comments
        if (comment.isDeleted()) {
            throw new CommentForbiddenException(comment.getId(), "Cannot edit a deleted comment");
        }

        // Only author can edit
        if (!comment.getUserId().equals(userId)) {
            throw new CommentForbiddenException(comment.getId(), "Only the author can edit this comment");
        }

        comment.edit(request.content());
        PlannerComment saved = commentRepository.save(comment);
        log.info("User {} edited comment {}", userId, commentPublicId);

        return new UpdateCommentResponse(saved.getEditedAt());
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
        comment.softDelete();
        commentRepository.save(comment);
        plannerStatsService.commentRemoved(comment.getPlannerId());
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

        // Only author can delete
        if (!comment.getUserId().equals(userId)) {
            throw new CommentForbiddenException(comment.getId(), "Only the author can delete this comment");
        }

        softDelete(comment);
        log.info("User {} deleted comment {}", userId, commentPublicId);
    }

    /**
     * Fan out a newly created comment to other nodes via Redis pub/sub,
     * carrying the full comment payload for real-time SSE delivery.
     *
     * @param plannerId    the planner the comment belongs to
     * @param saved        the persisted comment to broadcast
     * @param authorUserId the comment author's user ID
     */
    private void publishCommentCreated(UUID plannerId, PlannerComment saved, Long authorUserId) {
        User author = userService.findOptionalById(authorUserId).orElse(null);
        CommentTreeNode payload = CommentTreeNode.fromEntity(saved, author, null, false, List.of());
        ssePublisher.publishCommentEvent(plannerId, SseEventType.COMMENT_ADDED, saved.getPublicId().toString(), payload);
    }
}

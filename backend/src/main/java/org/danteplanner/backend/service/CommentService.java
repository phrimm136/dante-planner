package org.danteplanner.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.dto.comment.CommentResponse;
import org.danteplanner.backend.dto.comment.CommentVoteResponse;
import org.danteplanner.backend.dto.comment.CreateCommentRequest;
import org.danteplanner.backend.dto.comment.UpdateCommentRequest;
import org.danteplanner.backend.entity.CommentVoteType;
import org.danteplanner.backend.entity.PlannerComment;
import org.danteplanner.backend.entity.PlannerCommentVote;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.exception.CommentForbiddenException;
import org.danteplanner.backend.exception.CommentNotFoundException;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.repository.PlannerCommentRepository;
import org.danteplanner.backend.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for comment operations.
 * Handles CRUD, threading, and upvote toggle with atomic counters.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final PlannerCommentRepository commentRepository;
    private final PlannerCommentVoteRepository commentVoteRepository;
    private final PlannerRepository plannerRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * Get all comments for a planner as a flat list.
     * Includes deleted comments to preserve thread structure.
     * Frontend builds the tree structure from parentCommentId.
     *
     * @param plannerId     the planner ID
     * @param currentUserId the current user ID (null if unauthenticated)
     * @return list of comment responses
     * @throws PlannerNotFoundException if planner not found
     * @throws CommentForbiddenException if unpublished planner and not owner
     */
    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(UUID plannerId, Long currentUserId) {
        Planner planner = plannerRepository.findById(plannerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        // Check access: published planners are public, unpublished only for owner
        if (!planner.getPublished() && (currentUserId == null || !planner.getUser().getId().equals(currentUserId))) {
            throw new CommentForbiddenException("Cannot view comments on unpublished planner");
        }

        List<PlannerComment> comments = commentRepository.findByPlannerId(plannerId);
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch load users to avoid N+1 (exclude sentinel user - handled separately)
        Set<Long> userIds = comments.stream()
                .map(PlannerComment::getUserId)
                .filter(id -> !id.equals(CommentResponse.AuthorInfo.SENTINEL_USER_ID))
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        // Batch load vote status for authenticated user
        Set<Long> upvotedIds = Collections.emptySet();
        if (currentUserId != null) {
            List<Long> commentIds = comments.stream()
                    .map(PlannerComment::getId)
                    .collect(Collectors.toList());
            upvotedIds = new HashSet<>(commentVoteRepository.findUpvotedCommentIds(commentIds, currentUserId));
        }

        // Build response list
        Set<Long> finalUpvotedIds = upvotedIds;
        return comments.stream()
                .map(comment -> CommentResponse.fromEntity(
                        comment,
                        userMap.get(comment.getUserId()),
                        finalUpvotedIds.contains(comment.getId())
                ))
                .collect(Collectors.toList());
    }

    /**
     * Create a new comment on a planner.
     *
     * @param plannerId the planner ID
     * @param userId    the user ID
     * @param request   the create request
     * @return the created comment response
     * @throws PlannerNotFoundException if planner not found or not published
     * @throws CommentNotFoundException if parent comment not found
     * @throws CommentForbiddenException if replying to deleted top-level comment
     */
    @Transactional
    public CommentResponse createComment(UUID plannerId, Long userId, CreateCommentRequest request) {
        // Verify planner exists and is published
        Planner planner = plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        int depth = 0;
        Long effectiveParentId = request.parentCommentId();

        // Handle reply logic
        if (effectiveParentId != null) {
            final Long requestedParentId = effectiveParentId;
            PlannerComment parent = commentRepository.findById(requestedParentId)
                    .orElseThrow(() -> new CommentNotFoundException(requestedParentId));

            // Verify parent belongs to same planner
            if (!parent.getPlannerId().equals(plannerId)) {
                throw new CommentForbiddenException("Parent comment belongs to a different planner");
            }

            // Cannot reply to deleted TOP-LEVEL comments
            // But CAN reply to children of deleted comments (preserves thread continuity)
            if (parent.isDeleted() && parent.getDepth() == 0) {
                throw new CommentForbiddenException(requestedParentId, "Cannot reply to deleted top-level comment");
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

        User author = userRepository.findById(userId).orElseThrow();
        log.info("User {} created comment {} on planner {}", userId, saved.getId(), plannerId);

        // Send notifications
        if (effectiveParentId == null) {
            // Top-level comment - notify planner owner (if not self-comment)
            Long plannerOwnerId = planner.getUser().getId();
            if (!userId.equals(plannerOwnerId)) {
                notificationService.notifyCommentReceived(plannerId, plannerOwnerId, userId);
                log.debug("Sent COMMENT_RECEIVED notification to planner owner {}", plannerOwnerId);
            }
        } else {
            // Reply - notify parent comment author (if not self-reply)
            PlannerComment parentComment = commentRepository.findById(effectiveParentId).orElseThrow();
            Long parentAuthorId = parentComment.getUserId();
            if (!userId.equals(parentAuthorId)) {
                notificationService.notifyReplyReceived(effectiveParentId, parentAuthorId, userId);
                log.debug("Sent REPLY_RECEIVED notification to parent author {}", parentAuthorId);
            }
        }

        return CommentResponse.fromEntity(saved, author, false);
    }

    /**
     * Update a comment's content.
     * Only the comment author can edit.
     *
     * @param commentId the comment ID
     * @param userId    the user ID (must be author)
     * @param request   the update request
     * @return the updated comment response
     * @throws CommentNotFoundException if comment not found
     * @throws CommentForbiddenException if user is not author or comment is deleted
     */
    @Transactional
    public CommentResponse updateComment(Long commentId, Long userId, UpdateCommentRequest request) {
        PlannerComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));

        // Cannot edit deleted comments
        if (comment.isDeleted()) {
            throw new CommentForbiddenException(commentId, "Cannot edit a deleted comment");
        }

        // Only author can edit
        if (!comment.getUserId().equals(userId)) {
            throw new CommentForbiddenException(commentId, "Only the author can edit this comment");
        }

        comment.edit(request.content());
        PlannerComment saved = commentRepository.save(comment);

        User author = userRepository.findById(userId).orElseThrow();
        log.info("User {} edited comment {}", userId, commentId);

        // Check if user has upvoted their own comment (immutable votes - either exists or not)
        boolean hasUpvoted = commentVoteRepository.findByCommentIdAndUserId(commentId, userId)
                .isPresent();

        return CommentResponse.fromEntity(saved, author, hasUpvoted);
    }

    /**
     * Soft-delete a comment.
     * Only the comment author can delete their own comment.
     * Use ModerationService.deleteComment for moderator deletion.
     *
     * @param commentId the comment ID
     * @param userId    the user ID (must be author)
     * @throws CommentNotFoundException if comment not found
     * @throws CommentForbiddenException if user is not author
     */
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        PlannerComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));

        // Already deleted - idempotent
        if (comment.isDeleted()) {
            return;
        }

        // Only author can delete
        if (!comment.getUserId().equals(userId)) {
            throw new CommentForbiddenException(commentId, "Only the author can delete this comment");
        }

        comment.softDelete();
        commentRepository.save(comment);
        log.info("User {} deleted comment {}", userId, commentId);
    }

    /**
     * Cast an immutable upvote on a comment.
     * Votes are permanent - users can vote ONCE, with no removal allowed.
     * Uses atomic increment operations.
     *
     * @param commentId the comment ID
     * @param userId    the user ID
     * @return the vote response with updated count
     * @throws CommentNotFoundException if comment not found
     * @throws VoteAlreadyExistsException if user has already voted (409 Conflict)
     */
    @Transactional
    public CommentVoteResponse toggleUpvote(Long commentId, Long userId) {
        // Verify comment exists and is not deleted
        PlannerComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));

        // Cannot vote on deleted comments - they should only preserve thread structure
        if (comment.isDeleted()) {
            throw new CommentForbiddenException(commentId, "Cannot vote on a deleted comment");
        }

        // Check if vote already exists (immutability enforcement)
        var existingVote = commentVoteRepository.findByCommentIdAndUserId(commentId, userId);
        if (existingVote.isPresent()) {
            throw new org.danteplanner.backend.exception.VoteAlreadyExistsException(
                    comment.getPlannerId(), userId);
        }

        // Create new immutable vote
        PlannerCommentVote newVote = new PlannerCommentVote(commentId, userId, CommentVoteType.UP);
        commentVoteRepository.save(newVote);

        // Atomic increment
        int updated = commentRepository.incrementUpvoteCount(commentId);
        if (updated == 0) {
            log.warn("Failed to increment upvote count for comment {} - comment may have been deleted", commentId);
        }

        log.debug("User {} cast immutable upvote on comment {}", userId, commentId);

        // Re-fetch to get updated count after atomic operation
        int upvoteCount = commentRepository.findById(commentId)
                .map(PlannerComment::getUpvoteCount)
                .orElse(0);

        return new CommentVoteResponse(commentId, upvoteCount, true);
    }
}

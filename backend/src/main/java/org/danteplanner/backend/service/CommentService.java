package org.danteplanner.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.dto.comment.CreateCommentResponse;
import org.danteplanner.backend.dto.comment.CommentTreeNode;
import org.danteplanner.backend.dto.comment.CommentVoteResponse;
import org.danteplanner.backend.dto.comment.CreateCommentRequest;
import org.danteplanner.backend.dto.comment.ToggleNotificationResponse;
import org.danteplanner.backend.dto.comment.UpdateCommentRequest;
import org.danteplanner.backend.dto.comment.UpdateCommentResponse;
import org.danteplanner.backend.entity.CommentVoteType;
import org.danteplanner.backend.entity.PlannerComment;
import org.danteplanner.backend.entity.PlannerCommentVote;
import org.danteplanner.backend.entity.PlannerCommentVoteId;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.exception.CommentForbiddenException;
import org.danteplanner.backend.exception.CommentNotFoundException;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.exception.UserBannedException;
import org.danteplanner.backend.exception.UserNotFoundException;
import org.danteplanner.backend.exception.UserTimedOutException;
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
    private final PlannerCommentSseService plannerCommentSseService;

    /**
     * Check if user is restricted (timed out or banned).
     * Enforces write restrictions for comment operations.
     *
     * @param userId the user ID
     * @throws UserNotFoundException if user not found
     * @throws UserTimedOutException if user is currently timed out
     * @throws UserBannedException   if user is currently banned
     */
    private void checkUserRestrictions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.isTimedOut()) {
            throw new UserTimedOutException(userId, user.getTimeoutUntil());
        }

        if (user.isBanned()) {
            throw new UserBannedException(user.getId(), user.getBannedAt());
        }
    }

    /**
     * Get comments for a planner as a hierarchical tree.
     * Tree is built server-side with deleted comments without children pruned.
     *
     * @param plannerId     the planner ID
     * @param currentUserId the current user ID (null if unauthenticated)
     * @return hierarchical tree of comments
     * @throws PlannerNotFoundException if planner not found
     * @throws CommentForbiddenException if unpublished planner and not owner
     */
    @Transactional(readOnly = true)
    public List<CommentTreeNode> getCommentTree(UUID plannerId, Long currentUserId) {
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

        // Batch load users (exclude sentinel user ID 0)
        Set<Long> userIds = comments.stream()
                .map(PlannerComment::getUserId)
                .filter(id -> id != 0L)
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

        // Build tree structure
        return buildCommentTree(comments, userMap, upvotedIds, currentUserId);
    }

    /**
     * Build hierarchical comment tree from flat list.
     * Prunes deleted comments without children.
     * Sorts by createdAt ascending (oldest first).
     */
    private List<CommentTreeNode> buildCommentTree(
            List<PlannerComment> comments,
            Map<Long, User> userMap,
            Set<Long> upvotedIds,
            Long currentUserId
    ) {
        // Step 1: Create map of id -> comment
        Map<Long, PlannerComment> commentMap = comments.stream()
                .collect(Collectors.toMap(PlannerComment::getId, Function.identity()));

        // Step 2: Group by parentId
        Map<Long, List<PlannerComment>> childrenMap = comments.stream()
                .filter(c -> c.getParentCommentId() != null)
                .collect(Collectors.groupingBy(PlannerComment::getParentCommentId));

        // Step 3: Get top-level comments (no parent)
        List<PlannerComment> topLevel = comments.stream()
                .filter(c -> c.getParentCommentId() == null)
                .sorted(Comparator.comparing(PlannerComment::getCreatedAt))
                .collect(Collectors.toList());

        // Step 4: Recursively build tree nodes with pruning
        return topLevel.stream()
                .map(c -> buildNode(c, childrenMap, userMap, upvotedIds, currentUserId))
                .filter(Objects::nonNull)  // Prune deleted without children
                .collect(Collectors.toList());
    }

    /**
     * Recursively build a comment node.
     * Returns null if deleted AND has no children (pruned).
     */
    private CommentTreeNode buildNode(
            PlannerComment comment,
            Map<Long, List<PlannerComment>> childrenMap,
            Map<Long, User> userMap,
            Set<Long> upvotedIds,
            Long currentUserId
    ) {
        // Recursively build children first
        List<PlannerComment> children = childrenMap.getOrDefault(comment.getId(), Collections.emptyList());
        List<CommentTreeNode> childNodes = children.stream()
                .sorted(Comparator.comparing(PlannerComment::getCreatedAt))
                .map(c -> buildNode(c, childrenMap, userMap, upvotedIds, currentUserId))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Prune: if deleted AND no children, return null
        if (comment.isDeleted() && childNodes.isEmpty()) {
            return null;
        }

        // Build node
        User author = userMap.get(comment.getUserId());
        boolean hasUpvoted = upvotedIds.contains(comment.getId());

        return CommentTreeNode.fromEntity(comment, author, currentUserId, hasUpvoted, childNodes);
    }

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
        // Check if user has any restrictions (BUG FIX: was missing)
        checkUserRestrictions(userId);

        // Verify planner exists and is published
        Planner planner = plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        int depth = 0;
        Long effectiveParentId = null;

        // Handle reply logic - resolve UUID to internal ID
        if (request.parentCommentId() != null) {
            final UUID parentPublicId = request.parentCommentId();
            PlannerComment parent = commentRepository.findByPublicId(parentPublicId)
                    .orElseThrow(() -> new CommentNotFoundException(parentPublicId));
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
        log.info("User {} created comment {} on planner {}", userId, saved.getId(), plannerId);

        // Send notifications (respecting user notification settings)
        if (effectiveParentId == null) {
            // Top-level comment - notify planner owner (if not self-comment and owner has notifications enabled)
            Long plannerOwnerId = planner.getUser().getId();
            if (!userId.equals(plannerOwnerId) && Boolean.TRUE.equals(planner.getOwnerNotificationsEnabled())) {
                notificationService.notifyCommentReceived(
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
                notificationService.notifyReplyReceived(
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

        // Broadcast to other viewers (excluding author's device)
        plannerCommentSseService.broadcastCommentAdded(plannerId, deviceId);

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
        // Find parent comment by public ID
        PlannerComment parent = commentRepository.findByPublicId(parentPublicId)
                .orElseThrow(() -> new CommentNotFoundException(parentPublicId));

        UUID plannerId = parent.getPlannerId();

        // Verify planner exists and is published
        Planner planner = plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

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
        log.info("User {} created reply {} to comment {} on planner {}", userId, saved.getId(), parent.getId(), plannerId);

        // Send notification to parent author (if not self-reply and author has notifications enabled)
        PlannerComment notifyParent = commentRepository.findById(effectiveParentId).orElseThrow();
        Long parentAuthorId = notifyParent.getUserId();
        if (!userId.equals(parentAuthorId) && Boolean.TRUE.equals(notifyParent.getAuthorNotificationsEnabled())) {
            notificationService.notifyReplyReceived(
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

        // Broadcast to other viewers (excluding author's device)
        plannerCommentSseService.broadcastCommentAdded(plannerId, deviceId);

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
        PlannerComment comment = commentRepository.findByPublicId(commentPublicId)
                .orElseThrow(() -> new CommentNotFoundException(commentPublicId));

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
     * Soft-delete a comment.
     * Only the comment author can delete their own comment.
     * Use ModerationService.deleteComment for moderator deletion.
     *
     * @param commentPublicId the comment public UUID
     * @param userId          the user ID (must be author)
     * @throws CommentNotFoundException if comment not found
     * @throws CommentForbiddenException if user is not author
     */
    @Transactional
    public void deleteComment(UUID commentPublicId, Long userId) {
        PlannerComment comment = commentRepository.findByPublicId(commentPublicId)
                .orElseThrow(() -> new CommentNotFoundException(commentPublicId));

        // Already deleted - idempotent
        if (comment.isDeleted()) {
            return;
        }

        // Only author can delete
        if (!comment.getUserId().equals(userId)) {
            throw new CommentForbiddenException(comment.getId(), "Only the author can delete this comment");
        }

        comment.softDelete();
        commentRepository.save(comment);
        log.info("User {} deleted comment {}", userId, commentPublicId);
    }

    /**
     * Cast an immutable upvote on a comment.
     * Votes are permanent - users can vote ONCE, with no removal allowed.
     * Uses atomic increment operations.
     *
     * @param commentPublicId the comment public UUID
     * @param userId          the user ID
     * @return the vote response with updated count
     * @throws CommentNotFoundException if comment not found
     * @throws VoteAlreadyExistsException if user has already voted (409 Conflict)
     */
    @Transactional
    public CommentVoteResponse toggleUpvote(UUID commentPublicId, Long userId) {
        // Check if user has any restrictions
        checkUserRestrictions(userId);

        // Verify comment exists and is not deleted
        PlannerComment comment = commentRepository.findByPublicId(commentPublicId)
                .orElseThrow(() -> new CommentNotFoundException(commentPublicId));

        Long internalId = comment.getId();

        // Cannot vote on deleted comments - they should only preserve thread structure
        if (comment.isDeleted()) {
            throw new CommentForbiddenException(internalId, "Cannot vote on a deleted comment");
        }

        // Check if vote already exists (immutability enforcement)
        PlannerCommentVoteId voteId = new PlannerCommentVoteId(internalId, userId);
        if (commentVoteRepository.existsById(voteId)) {
            throw new org.danteplanner.backend.exception.VoteAlreadyExistsException(
                    comment.getPlannerId(), userId);
        }

        // Create new immutable vote
        PlannerCommentVote newVote = new PlannerCommentVote(internalId, userId, CommentVoteType.UP);
        commentVoteRepository.save(newVote);

        // Atomic increment
        int updated = commentRepository.incrementUpvoteCount(internalId);
        if (updated == 0) {
            log.warn("Failed to increment upvote count for comment {} - comment may have been deleted", commentPublicId);
        }

        log.debug("User {} cast immutable upvote on comment {}", userId, commentPublicId);

        // Re-fetch to get updated count after atomic operation
        int upvoteCount = commentRepository.findById(internalId)
                .map(PlannerComment::getUpvoteCount)
                .orElse(0);

        return new CommentVoteResponse(commentPublicId, upvoteCount, true);
    }

    /**
     * Toggle notification setting for a comment author.
     * Only the comment author can toggle their own notification setting.
     *
     * @param commentPublicId the comment public UUID
     * @param userId          the user ID (must be author)
     * @param enabled         the new notification setting
     * @return the toggle result
     * @throws CommentNotFoundException if comment not found
     * @throws CommentForbiddenException if user is not author
     */
    @Transactional
    public ToggleNotificationResponse toggleNotification(UUID commentPublicId, Long userId, boolean enabled) {
        PlannerComment comment = commentRepository.findByPublicId(commentPublicId)
                .orElseThrow(() -> new CommentNotFoundException(commentPublicId));

        // Only author can toggle their notification setting
        if (!comment.getUserId().equals(userId)) {
            throw new CommentForbiddenException(comment.getId(), "Only the author can toggle notification settings");
        }

        comment.setAuthorNotificationsEnabled(enabled);
        commentRepository.save(comment);

        log.info("User {} toggled notifications {} for comment {}", userId, enabled ? "on" : "off", commentPublicId);

        return new ToggleNotificationResponse(enabled);
    }
}

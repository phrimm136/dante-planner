package org.danteplanner.backend.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.comment.dto.CommentVoteResponse;
import org.danteplanner.backend.comment.dto.ToggleNotificationResponse;
import org.danteplanner.backend.comment.entity.CommentVoteType;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.entity.PlannerCommentVote;
import org.danteplanner.backend.comment.entity.PlannerCommentVoteId;
import org.danteplanner.backend.comment.exception.CommentForbiddenException;
import org.danteplanner.backend.comment.exception.CommentNotFoundException;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.planner.exception.VoteAlreadyExistsException;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Social actions a reader takes on a comment: the immutable upvote, and the author's own
 * notification preference for the thread beneath it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommentEngagementService {

    private final PlannerCommentRepository commentRepository;
    private final PlannerCommentVoteRepository commentVoteRepository;
    private final CommentQueryService commentQueryService;
    private final PlannerAccessGuard accessGuard;

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
        accessGuard.checkNotBanned(userId);

        PlannerComment comment = commentQueryService.requireByPublicId(commentPublicId);

        Long internalId = comment.getId();

        // Cannot vote on deleted comments - they should only preserve thread structure
        if (comment.isDeleted()) {
            throw new CommentForbiddenException(internalId, "Cannot vote on a deleted comment");
        }

        // Check if vote already exists (immutability enforcement)
        PlannerCommentVoteId voteId = new PlannerCommentVoteId(internalId, userId);
        if (commentVoteRepository.existsById(voteId)) {
            throw new VoteAlreadyExistsException(comment.getPlannerId(), userId);
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
        PlannerComment comment = commentQueryService.requireByPublicId(commentPublicId);

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

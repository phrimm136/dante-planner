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
import org.danteplanner.backend.moderation.dto.CommentReportRequest;
import org.danteplanner.backend.moderation.dto.CommentReportResponse;
import org.danteplanner.backend.moderation.exception.CommentReportAlreadyExistsException;
import org.danteplanner.backend.moderation.service.CommentReportService;
import org.danteplanner.backend.planner.exception.VoteAlreadyExistsException;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Social actions a reader takes on a comment: the immutable upvote, the report, and the author's
 * own notification preference for the thread beneath it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommentEngagementService {

    private final PlannerCommentRepository commentRepository;
    private final PlannerCommentVoteRepository commentVoteRepository;
    private final CommentQueryService commentQueryService;
    private final PlannerAccessGuard accessGuard;
    private final CommentReportService reportService;

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

        commentRepository.incrementUpvoteCount(internalId);

        log.debug("User {} cast immutable upvote on comment {}", userId, commentPublicId);

        // The increment clears the persistence context, so this reads the committed counter rather
        // than the pre-increment copy the vote was checked against.
        PlannerComment counted = commentRepository.findById(internalId)
                .orElseThrow(() -> new CommentNotFoundException(commentPublicId));

        return new CommentVoteResponse(commentPublicId, counted.getUpvoteCount(), true);
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

    /**
     * Report a comment on a user's behalf.
     *
     * @param commentPublicId the comment public UUID being reported
     * @param userId          the reporting user ID
     * @param request         the report request with reason
     * @return the report timestamp
     * @throws CommentNotFoundException            if comment not found
     * @throws CommentForbiddenException           if comment is deleted
     * @throws CommentReportAlreadyExistsException if the user has already reported this comment
     */
    public CommentReportResponse reportComment(UUID commentPublicId, Long userId, CommentReportRequest request) {
        return reportService.createReport(commentPublicId, userId, request);
    }
}

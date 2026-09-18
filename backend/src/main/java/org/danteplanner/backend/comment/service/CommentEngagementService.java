package org.danteplanner.backend.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.comment.dto.CommentVoteResponse;
import org.danteplanner.backend.comment.dto.ToggleNotificationResponse;
import org.danteplanner.backend.comment.entity.CommentVoteType;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.entity.PlannerCommentVote;
import org.danteplanner.backend.comment.entity.PlannerCommentVoteId;
import org.danteplanner.backend.comment.exception.CommentNotFoundException;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.comment.validation.CommentAuthorshipValidator;
import org.danteplanner.backend.comment.validation.CommentStateValidator;
import org.danteplanner.backend.moderation.dto.CommentReportRequest;
import org.danteplanner.backend.moderation.dto.CommentReportResponse;
import org.danteplanner.backend.moderation.service.CommentReportService;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.validation.VoteUniquenessValidator;
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
    private final CommentAuthorshipValidator authorshipValidator;
    private final CommentStateValidator stateValidator;
    private final VoteUniquenessValidator voteUniquenessValidator;

    @Transactional
    public CommentVoteResponse toggleUpvote(UUID commentPublicId, Long userId) {
        accessGuard.checkNotBanned(userId);

        PlannerComment comment = commentQueryService.requireByPublicId(commentPublicId);
        Long internalId = comment.getId();

        stateValidator.requireVotable(comment);

        PlannerCommentVoteId voteId = new PlannerCommentVoteId(internalId, userId);
        voteUniquenessValidator.requireFirstVote(
                commentVoteRepository.existsById(voteId), comment.getPlannerId(), userId);

        PlannerCommentVote newVote = new PlannerCommentVote(internalId, userId, CommentVoteType.UP);

        commentVoteRepository.insert(newVote);
        commentRepository.incrementUpvoteCount(internalId);

        // The increment clears the persistence context, so this reads the committed counter rather
        // than the pre-increment copy the vote was checked against.
        PlannerComment counted = commentRepository.findById(internalId)
                .orElseThrow(() -> new CommentNotFoundException(commentPublicId));
        CommentVoteResponse response = new CommentVoteResponse(commentPublicId, counted.getUpvoteCount(), true);

        log.debug("User {} cast immutable upvote on comment {}", userId, commentPublicId);

        return response;
    }

    @Transactional
    public ToggleNotificationResponse toggleNotification(UUID commentPublicId, Long userId, boolean enabled) {
        PlannerComment comment = commentQueryService.requireByPublicId(commentPublicId);

        authorshipValidator.requireAuthorToToggleNotifications(comment, userId);

        comment.setAuthorNotificationsEnabled(enabled);

        ToggleNotificationResponse response = new ToggleNotificationResponse(enabled);

        log.info("User {} toggled notifications {} for comment {}", userId, enabled ? "on" : "off", commentPublicId);

        return response;
    }

    public CommentReportResponse reportComment(UUID commentPublicId, Long userId, CommentReportRequest request) {
        return reportService.createReport(commentPublicId, userId, request);
    }
}

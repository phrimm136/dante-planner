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
import org.danteplanner.backend.comment.validation.CommentAuthorshipValidator.AuthoredComment;
import org.danteplanner.backend.comment.validation.CommentRefusal;
import org.danteplanner.backend.comment.validation.CommentStateValidator.LiveComment;
import org.danteplanner.backend.moderation.api.dto.CommentReportRequest;
import org.danteplanner.backend.moderation.api.dto.CommentReportResponse;
import org.danteplanner.backend.moderation.api.CommentReports;
import org.danteplanner.backend.planner.api.PlannerVotes;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
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
    private final CommentReports reportService;
    private final PlannerVotes plannerVotes;

    @Transactional
    public CommentVoteResponse toggleUpvote(UUID commentPublicId, Long userId) {
        LiveComment live = LiveComment
                .of(commentQueryService.requireByPublicId(commentPublicId), CommentRefusal.VOTE_DELETED)
                .orElseThrow(CommentRefusal::toException);
        Long internalId = live.comment().getId();

        PlannerCommentVoteId voteId = new PlannerCommentVoteId(internalId, userId);
        plannerVotes.requireFirstVote(
                commentVoteRepository.existsById(voteId), live.comment().getPlannerId(), userId);

        PlannerCommentVote newVote = new PlannerCommentVote(internalId, userId, CommentVoteType.UP);

        commentVoteRepository.insert(newVote);
        commentRepository.incrementUpvoteCount(internalId);
        FreshnessDeclarations.declare(new FreshnessScope.Planner(live.comment().getPlannerId()));

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
        AuthoredComment authored = AuthoredComment
                .of(commentQueryService.requireByPublicId(commentPublicId), userId, CommentRefusal.TOGGLE_NOT_AUTHOR)
                .orElseThrow(CommentRefusal::toException);

        authored.comment().setAuthorNotificationsEnabled(enabled);
        FreshnessDeclarations.declare(new FreshnessScope.Planner(authored.comment().getPlannerId()));

        ToggleNotificationResponse response = new ToggleNotificationResponse(enabled);

        log.info("User {} toggled notifications {} for comment {}", userId, onOrOff(enabled), commentPublicId);

        return response;
    }

    private static String onOrOff(boolean enabled) {
        return enabled ? "on" : "off";
    }

    public CommentReportResponse reportComment(UUID commentPublicId, Long userId, CommentReportRequest request) {
        return reportService.createReport(commentPublicId, userId, request);
    }
}

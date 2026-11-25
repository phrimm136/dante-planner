package org.danteplanner.backend.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.comment.dto.CommentVoteResponse;
import org.danteplanner.backend.comment.dto.ToggleNotificationResponse;
import org.danteplanner.backend.comment.entity.CommentVoteType;
import org.danteplanner.backend.comment.repository.PlannerCommentContentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentStatsRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.comment.validation.CommentAuthorshipValidator.AuthoredComment;
import org.danteplanner.backend.comment.validation.CommentRefusal;
import org.danteplanner.backend.comment.validation.CommentStateValidator;
import org.danteplanner.backend.comment.validation.CommentStateValidator.LiveComment;
import org.danteplanner.backend.moderation.api.dto.CommentReportRequest;
import org.danteplanner.backend.moderation.api.dto.CommentReportResponse;
import org.danteplanner.backend.moderation.api.CommentReports;
import org.danteplanner.backend.planner.api.PlannerVotes;
import org.danteplanner.backend.shared.failure.WriteOutcome;
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

    private final PlannerCommentContentRepository contentRepository;
    private final PlannerCommentStatsRepository statsRepository;
    private final PlannerCommentVoteRepository commentVoteRepository;
    private final CommentQueryService commentQueryService;
    private final CommentReports reportService;
    private final PlannerVotes plannerVotes;

    @Transactional
    public CommentVoteResponse toggleUpvote(UUID commentPublicId, Long userId) {
        LiveComment live = LiveComment
                .of(commentQueryService.requireByPublicId(commentPublicId), CommentRefusal.VOTE_DELETED)
                .orElseThrow(CommentRefusal::toException);
        Long commentId = live.comment().id();
        UUID plannerId = live.comment().plannerId();

        switch (WriteOutcome.of(commentVoteRepository.insertIgnore(commentId, userId, CommentVoteType.UP.name()))) {
            case APPLIED -> {
                statsRepository.incrementUpvoteCount(commentId);
                FreshnessDeclarations.declare(new FreshnessScope.Planner(plannerId));
            }
            case NOTHING -> plannerVotes.requireFirstVote(true, plannerId, userId);
        }

        CommentVoteResponse response = new CommentVoteResponse(
                commentPublicId, statsRepository.upvoteCountOf(commentId), true);

        log.debug("User {} cast immutable upvote on comment {}", userId, commentPublicId);

        return response;
    }

    @Transactional
    public ToggleNotificationResponse toggleNotification(UUID commentPublicId, Long userId, boolean enabled) {
        AuthoredComment authored = AuthoredComment
                .of(commentQueryService.requireByPublicId(commentPublicId), userId, CommentRefusal.TOGGLE_NOT_AUTHOR)
                .orElseThrow(CommentRefusal::toException);

        NotificationChoice choice = NotificationChoice.of(enabled);
        int matched = switch (choice) {
            case ON -> contentRepository.enableAuthorNotifications(authored.comment().id());
            case OFF -> contentRepository.disableAuthorNotifications(authored.comment().id());
        };
        switch (WriteOutcome.of(matched)) {
            case APPLIED ->
                    FreshnessDeclarations.declare(new FreshnessScope.Planner(authored.comment().plannerId()));
            case NOTHING -> throw CommentStateValidator.gone(commentPublicId);
        }

        ToggleNotificationResponse response = new ToggleNotificationResponse(enabled);

        log.info("User {} toggled notifications {} for comment {}", userId, choice.label, commentPublicId);

        return response;
    }

    private enum NotificationChoice {
        ON("on"),
        OFF("off");

        private final String label;

        NotificationChoice(String label) {
            this.label = label;
        }

        static NotificationChoice of(boolean enabled) {
            return enabled ? ON : OFF;
        }
    }

    public CommentReportResponse reportComment(UUID commentPublicId, Long userId, CommentReportRequest request) {
        return reportService.createReport(commentPublicId, userId, request);
    }
}

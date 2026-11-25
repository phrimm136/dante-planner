package org.danteplanner.backend.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.comment.dto.CreateCommentRequest;
import org.danteplanner.backend.comment.dto.CreateCommentResponse;
import org.danteplanner.backend.comment.dto.UpdateCommentRequest;
import org.danteplanner.backend.comment.dto.UpdateCommentResponse;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.CommentRow;
import org.danteplanner.backend.comment.repository.PlannerCommentContentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentStatsRepository;
import org.danteplanner.backend.comment.validation.CommentAuthorshipValidator;
import org.danteplanner.backend.comment.validation.CommentAuthorshipValidator.AuthoredComment;
import org.danteplanner.backend.comment.validation.CommentRefusal;
import org.danteplanner.backend.comment.validation.CommentStateValidator;
import org.danteplanner.backend.comment.validation.CommentStateValidator.ReplyableParent;
import org.danteplanner.backend.planner.api.PlannerAccess;
import org.danteplanner.backend.planner.api.PlannerCommentCounts;
import org.danteplanner.backend.shared.failure.WriteOutcome;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import org.danteplanner.backend.shared.util.CommentConstants;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
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
    private final PlannerCommentContentRepository contentRepository;
    private final PlannerCommentStatsRepository statsRepository;
    private final CommentQueryService commentQueryService;
    private final DomainEventRecorder domainEventRecorder;
    private final PlannerAccess plannerAccess;
    private final PlannerCommentCounts plannerCommentCounts;
    private final Clock clock;

    @Transactional
    public CreateCommentResponse createComment(UUID plannerId, Long userId, UUID deviceId, CreateCommentRequest request) {
        plannerAccess.checkPublished(plannerId);

        Placement placement = switch (Addressee.of(request.parentCommentId())) {
            case Addressee.Thread thread -> new Placement.Parentless(0);
            case Addressee.Parent(UUID parentPublicId) -> Placement.under(CommentStateValidator
                    .replyableIn(commentQueryService.requireByPublicId(parentPublicId), plannerId)
                    .orElseThrow(CommentRefusal::toException));
        };

        PlannerComment saved = insert(
                placement.row(plannerId, userId, request.content()), placement.announcement());

        log.info("User {} created comment {} on planner {}", userId, saved.getId(), plannerId);

        return new CreateCommentResponse(saved.getPublicId(), saved.getCreatedAt());
    }

    @Transactional
    public CreateCommentResponse createReply(UUID parentPublicId, Long userId, UUID deviceId, String content) {
        CommentRow parent = commentQueryService.requireByPublicId(parentPublicId);

        UUID plannerId = parent.plannerId();

        plannerAccess.checkPublished(plannerId);

        Placement placement = Placement.under(CommentStateValidator.replyableIn(parent, plannerId)
                .orElseThrow(CommentRefusal::toException));

        PlannerComment saved = insert(placement.row(plannerId, userId, content), Announcement.REPLY);

        log.info("User {} created reply {} to comment {} on planner {}", userId, saved.getId(), parent.id(), plannerId);

        return new CreateCommentResponse(saved.getPublicId(), saved.getCreatedAt());
    }

    private PlannerComment insert(PlannerComment row, Announcement announcement) {
        PlannerComment saved = commentRepository.insert(row);
        contentRepository.insertContent(saved.getId(), saved.getContent());
        statsRepository.insertStats(saved.getId());
        plannerCommentCounts.incrementCommentCount(row.getPlannerId());
        declareCountChanged(row.getPlannerId());
        domainEventRecorder.recordDomainEvent(announcement.event, row.getPlannerId(),
                Map.of(announcement.idKey, saved.getId()));
        return saved;
    }

    private sealed interface Addressee {

        record Thread() implements Addressee {
        }

        record Parent(UUID publicId) implements Addressee {
        }

        static Addressee of(@Nullable UUID parentPublicId) {
            return parentPublicId == null ? new Thread() : new Parent(parentPublicId);
        }
    }

    private enum Announcement {
        COMMENT(DomainEventType.COMMENT_RECEIVED, "commentId"),
        REPLY(DomainEventType.REPLY_RECEIVED, "replyId");

        private final DomainEventType event;
        private final String idKey;

        Announcement(DomainEventType event, String idKey) {
            this.event = event;
            this.idKey = idKey;
        }
    }

    private sealed interface Placement {

        record UnderParent(Long parentId, int depth) implements Placement {
        }

        record Parentless(int depth) implements Placement {
        }

        static Placement under(ReplyableParent replyable) {
            CommentRow parent = replyable.comment();
            int depth = Math.min(parent.depth() + 1, CommentConstants.MAX_DEPTH);
            Long effectiveParentId = parent.depth() >= CommentConstants.MAX_DEPTH
                    ? parent.parentCommentId()
                    : parent.id();
            return effectiveParentId == null ? new Parentless(depth) : new UnderParent(effectiveParentId, depth);
        }

        default PlannerComment row(UUID plannerId, Long userId, String content) {
            return switch (this) {
                case UnderParent(Long parentId, int depth) ->
                        new PlannerComment(plannerId, userId, content, parentId, depth);
                case Parentless(int depth) -> new PlannerComment(plannerId, userId, content, null, depth);
            };
        }

        default Announcement announcement() {
            return switch (this) {
                case UnderParent underParent -> Announcement.REPLY;
                case Parentless parentless -> Announcement.COMMENT;
            };
        }
    }

    @Transactional
    public UpdateCommentResponse updateComment(UUID commentPublicId, Long userId, UpdateCommentRequest request) {
        AuthoredComment authored = CommentAuthorshipValidator
                .editableBy(commentQueryService.requireByPublicId(commentPublicId), userId)
                .orElseThrow(CommentRefusal::toException);
        Instant editedAt = clock.instant();

        switch (WriteOutcome.of(contentRepository.edit(authored.comment().id(), request.content(), editedAt))) {
            case APPLIED -> {
                FreshnessDeclarations.declare(new FreshnessScope.Planner(authored.comment().plannerId()));
                log.info("User {} edited comment {}", userId, commentPublicId);
            }
            case NOTHING -> throw CommentRefusal.EDIT_DELETED.toException();
        }

        return new UpdateCommentResponse(editedAt);
    }

    @Transactional
    public WriteOutcome softDelete(CommentRow comment) {
        WriteOutcome withdrawn = WriteOutcome.of(contentRepository.withdraw(
                comment.id(), CommentConstants.DELETED_CONTENT, clock.instant()));
        switch (withdrawn) {
            case APPLIED -> {
                plannerCommentCounts.decrementCommentCount(comment.plannerId());
                declareCountChanged(comment.plannerId());
            }
            case NOTHING -> { }
        }
        return withdrawn;
    }

    private static void declareCountChanged(UUID plannerId) {
        FreshnessDeclarations.declare(new FreshnessScope.Planner(plannerId), new FreshnessScope.Global());
    }

    @Transactional
    public void deleteComment(UUID commentPublicId, Long userId) {
        CommentRow comment = commentQueryService.requireByPublicId(commentPublicId);

        switch (Withdrawal.of(comment)) {
            case ALREADY_DONE -> { }
            case DUE -> {
                AuthoredComment authored = AuthoredComment.of(comment, userId, CommentRefusal.DELETE_NOT_AUTHOR)
                        .orElseThrow(CommentRefusal::toException);
                switch (softDelete(authored.comment())) {
                    case APPLIED -> log.info("User {} deleted comment {}", userId, commentPublicId);
                    case NOTHING -> { }
                }
            }
        }
    }

    private enum Withdrawal {
        ALREADY_DONE,
        DUE;

        static Withdrawal of(CommentRow comment) {
            return comment.isDeleted() ? ALREADY_DONE : DUE;
        }
    }
}

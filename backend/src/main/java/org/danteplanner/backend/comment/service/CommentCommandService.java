package org.danteplanner.backend.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.comment.dto.CreateCommentRequest;
import org.danteplanner.backend.comment.dto.CreateCommentResponse;
import org.danteplanner.backend.comment.dto.UpdateCommentRequest;
import org.danteplanner.backend.comment.dto.UpdateCommentResponse;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.validation.CommentAuthorshipValidator;
import org.danteplanner.backend.comment.validation.CommentAuthorshipValidator.AuthoredComment;
import org.danteplanner.backend.comment.validation.CommentRefusal;
import org.danteplanner.backend.comment.validation.CommentStateValidator;
import org.danteplanner.backend.comment.validation.CommentStateValidator.ReplyableParent;
import org.danteplanner.backend.planner.api.PlannerAccess;
import org.danteplanner.backend.planner.api.PlannerCommentCounts;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import org.danteplanner.backend.shared.util.CommentConstants;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
        PlannerComment parent = commentQueryService.requireByPublicId(parentPublicId);

        UUID plannerId = parent.getPlannerId();

        plannerAccess.checkPublished(plannerId);

        Placement placement = Placement.under(CommentStateValidator.replyableIn(parent, plannerId)
                .orElseThrow(CommentRefusal::toException));

        PlannerComment saved = insert(placement.row(plannerId, userId, content), Announcement.REPLY);

        log.info("User {} created reply {} to comment {} on planner {}", userId, saved.getId(), parent.getId(), plannerId);

        return new CreateCommentResponse(saved.getPublicId(), saved.getCreatedAt());
    }

    private PlannerComment insert(PlannerComment row, Announcement announcement) {
        PlannerComment saved = commentRepository.insert(row);
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
            PlannerComment parent = replyable.comment();
            int depth = Math.min(parent.getDepth() + 1, CommentConstants.MAX_DEPTH);
            Long effectiveParentId = parent.getDepth() >= CommentConstants.MAX_DEPTH
                    ? parent.getParentCommentId()
                    : parent.getId();
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

        authored.comment().edit(request.content(), clock.instant());
        FreshnessDeclarations.declare(new FreshnessScope.Planner(authored.comment().getPlannerId()));
        log.info("User {} edited comment {}", userId, commentPublicId);

        return new UpdateCommentResponse(authored.comment().getEditedAt());
    }

    @Transactional
    public void softDelete(PlannerComment comment) {
        // The withdrawal must precede the counter: decrementing detaches the comment, and a
        // mutation made after it would never reach the row.
        comment.softDelete(clock.instant());
        plannerCommentCounts.decrementCommentCount(comment.getPlannerId());
        declareCountChanged(comment.getPlannerId());
    }

    private static void declareCountChanged(UUID plannerId) {
        FreshnessDeclarations.declare(new FreshnessScope.Planner(plannerId), new FreshnessScope.Global());
    }

    @Transactional
    public void deleteComment(UUID commentPublicId, Long userId) {
        PlannerComment comment = commentQueryService.requireByPublicId(commentPublicId);

        switch (Withdrawal.of(comment)) {
            case ALREADY_DONE -> { }
            case DUE -> {
                AuthoredComment authored = AuthoredComment.of(comment, userId, CommentRefusal.DELETE_NOT_AUTHOR)
                        .orElseThrow(CommentRefusal::toException);
                softDelete(authored.comment());
                log.info("User {} deleted comment {}", userId, commentPublicId);
            }
        }
    }

    private enum Withdrawal {
        ALREADY_DONE,
        DUE;

        static Withdrawal of(PlannerComment comment) {
            return comment.isDeleted() ? ALREADY_DONE : DUE;
        }
    }
}

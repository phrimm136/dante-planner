package org.danteplanner.backend.comment.service;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.validation.CommentAuthorshipValidator;
import org.danteplanner.backend.comment.validation.CommentAuthorshipValidator.AuthoredComment;
import org.danteplanner.backend.comment.validation.CommentRefusal;
import org.danteplanner.backend.comment.validation.CommentStateValidator;
import org.danteplanner.backend.comment.validation.CommentStateValidator.LiveComment;
import org.danteplanner.backend.shared.failure.Result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;

class CommentCheckChainTest {

    private static final UUID PLANNER_ID = UUID.randomUUID();
    private static final UUID OTHER_PLANNER_ID = UUID.randomUUID();
    private static final Long AUTHOR_ID = 2L;
    private static final Long STRANGER_ID = 3L;

    @Test
    void editableBy_WhenLiveAndByItsAuthor_HoldsTheComment() {
        PlannerComment comment = live(0);

        assertThat(CommentAuthorshipValidator.editableBy(comment, AUTHOR_ID)
                .orElseThrow(CommentRefusal::toException).comment()).isSameAs(comment);
    }

    @Test
    void editableBy_WhenDeleted_IsRefusedForTheDeletionWhoeverAsks() {
        assertThat(CommentAuthorshipValidator.editableBy(deleted(0), AUTHOR_ID))
                .isEqualTo(Result.err(CommentRefusal.EDIT_DELETED));
        assertThat(CommentAuthorshipValidator.editableBy(deleted(0), STRANGER_ID))
                .isEqualTo(Result.err(CommentRefusal.EDIT_DELETED));
    }

    @Test
    void editableBy_WhenLiveAndByAStranger_IsRefusedForTheAuthorship() {
        assertThat(CommentAuthorshipValidator.editableBy(live(0), STRANGER_ID))
                .isEqualTo(Result.err(CommentRefusal.EDIT_NOT_AUTHOR));
    }

    @Test
    void replyableIn_WhenTheParentIsDeletedTopLevelInAnotherPlanner_IsRefusedForThePlanner() {
        assertThat(CommentStateValidator.replyableIn(deleted(0), OTHER_PLANNER_ID))
                .isEqualTo(Result.err(CommentRefusal.OTHER_PLANNER));
    }

    @Test
    void replyableIn_WhenTheParentIsDeletedTopLevelInThisPlanner_IsRefusedForTheDeletion() {
        assertThat(CommentStateValidator.replyableIn(deleted(0), PLANNER_ID))
                .isEqualTo(Result.err(CommentRefusal.REPLY_DELETED));
    }

    @Test
    void replyableIn_WhenTheParentIsADeletedReplyInThisPlanner_HoldsTheParent() {
        PlannerComment parent = deleted(1);

        assertThat(CommentStateValidator.replyableIn(parent, PLANNER_ID)
                .orElseThrow(CommentRefusal::toException).comment()).isSameAs(parent);
    }

    @Test
    void liveComment_WhenDeleted_CarriesTheGivenRefusal() {
        assertThat(LiveComment.of(deleted(0), CommentRefusal.VOTE_DELETED))
                .isEqualTo(Result.err(CommentRefusal.VOTE_DELETED));
        assertThat(LiveComment.of(deleted(0), CommentRefusal.REPORT_DELETED))
                .isEqualTo(Result.err(CommentRefusal.REPORT_DELETED));
    }

    @Test
    void authoredComment_WhenByAStranger_CarriesTheGivenRefusal() {
        assertThat(AuthoredComment.of(live(0), STRANGER_ID, CommentRefusal.DELETE_NOT_AUTHOR))
                .isEqualTo(Result.err(CommentRefusal.DELETE_NOT_AUTHOR));
        assertThat(AuthoredComment.of(live(0), STRANGER_ID, CommentRefusal.TOGGLE_NOT_AUTHOR))
                .isEqualTo(Result.err(CommentRefusal.TOGGLE_NOT_AUTHOR));
    }

    @ParameterizedTest
    @CsvSource({
            "EDIT_DELETED, Cannot edit a deleted comment",
            "VOTE_DELETED, Cannot vote on a deleted comment",
            "REPORT_DELETED, Cannot report a deleted comment",
            "EDIT_NOT_AUTHOR, Only the author can edit this comment",
            "DELETE_NOT_AUTHOR, Only the author can delete this comment",
            "TOGGLE_NOT_AUTHOR, Only the author can toggle notification settings",
            "OTHER_PLANNER, Parent comment belongs to a different planner",
            "REPLY_DELETED, Cannot reply to deleted top-level comment"})
    void toException_WhenRefused_IsForbiddenWithTheMessage(CommentRefusal refusal, String message) {
        assertRejected(() -> {
            throw refusal.toException();
        }).withCode("COMMENT_FORBIDDEN").satisfies(refused -> assertThat(refused).hasMessage(message));
    }

    private static PlannerComment live(int depth) {
        return new PlannerComment(PLANNER_ID, AUTHOR_ID, "text", depth == 0 ? null : 40L, depth);
    }

    private static PlannerComment deleted(int depth) {
        PlannerComment comment = live(depth);
        comment.softDelete(Instant.EPOCH);
        return comment;
    }
}

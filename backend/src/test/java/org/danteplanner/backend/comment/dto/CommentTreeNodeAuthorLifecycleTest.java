package org.danteplanner.backend.comment.dto;

import org.danteplanner.backend.user.service.UsersApiAdapter;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.shared.util.CommentConstants;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A deactivated author's comment is projected with the same author-hidden predicate for its body
 * as for its name, while the comment itself stays in the tree.
 */
class CommentTreeNodeAuthorLifecycleTest {

    private static User author(String epithet) {
        return TestDataFactory.user()
                .id(3L)
                .usernameEpithet(epithet)
                .build();
    }

    private static PlannerComment comment(Long userId) {
        PlannerComment comment = new PlannerComment(UUID.randomUUID(), userId, "still here", null, 0);
        comment.setId(11L);
        comment.setPublicId(UUID.randomUUID());
        comment.setCreatedAt(Instant.now());
        return comment;
    }

    private static CommentTreeNode project(PlannerComment comment, User author) {
        return CommentTreeNode.fromEntity(
                comment, null, author == null ? null : UsersApiAdapter.toSummary(author), null, false, List.of());
    }

    @Test
    void fromEntity_WhenAuthorDeactivatedInGraceWindow_BlanksTheBody() {
        User author = author("GONE");
        author.softDelete(Instant.now().plus(Duration.ofDays(30)));
        PlannerComment comment = comment(author.getId());

        CommentTreeNode node = project(comment, author);

        assertThat(node.authorEpithet()).isEqualTo(CommentConstants.DELETED_CONTENT);
        assertThat(node.authorSuffix()).isEqualTo(CommentConstants.DELETED_CONTENT);
        assertThat(node.isDeleted()).as("the comment row itself is not withdrawn").isFalse();
        assertThat(node.content())
                .as("the author-hidden predicate governs the body as it does the name")
                .isEqualTo(CommentConstants.DELETED_CONTENT);
    }

    @Test
    void fromEntity_WhenAuthorDeactivated_LeavesTheEntityContent() {
        User author = author("GONE");
        author.softDelete(Instant.now().plus(Duration.ofDays(30)));
        PlannerComment comment = comment(author.getId());

        project(comment, author);

        assertThat(comment.getContent()).isEqualTo("still here");
    }

    @Test
    void fromEntity_WhenAuthorReactivated_ServesTheBody() {
        User author = author("GONE");
        author.softDelete(Instant.now().plus(Duration.ofDays(30)));
        PlannerComment comment = comment(author.getId());
        project(comment, author);

        author.reactivate();
        CommentTreeNode node = project(comment, author);

        assertThat(node.content()).isEqualTo("still here");
        assertThat(node.authorEpithet()).isEqualTo("GONE");
    }

    @Test
    void fromEntity_WhenAuthorMissing_BlanksTheBody() {
        CommentTreeNode node = project(comment(3L), null);

        assertThat(node.content()).isEqualTo(CommentConstants.DELETED_CONTENT);
        assertThat(node.authorEpithet()).isEqualTo(CommentConstants.DELETED_CONTENT);
    }

    @Test
    void fromEntity_WhenLiveAuthorHasNoEpithet_MasksTheNameAndServesTheBody() {
        User author = author(null);

        CommentTreeNode node = project(comment(author.getId()), author);

        assertThat(node.authorEpithet()).isEqualTo(CommentConstants.DELETED_CONTENT);
        assertThat(node.content()).isEqualTo("still here");
    }

    @Test
    void fromEntity_WhenCommentDeletedAndAuthorDeactivated_BlanksTheBody() {
        User author = author("GONE");
        author.softDelete(Instant.now().plus(Duration.ofDays(30)));
        PlannerComment comment = comment(author.getId());
        comment.softDelete();

        CommentTreeNode node = project(comment, author);

        assertThat(node.content()).isEqualTo(CommentConstants.DELETED_CONTENT);
        assertThat(node.isDeleted()).isTrue();
    }
}

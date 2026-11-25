package org.danteplanner.backend.comment.dto;

import org.danteplanner.backend.support.CommentRows;
import org.danteplanner.backend.comment.repository.CommentRow;
import org.danteplanner.backend.user.service.UsersApiAdapter;
import org.danteplanner.backend.shared.util.CommentConstants;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.shared.security.Viewer;
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

    private static CommentRow comment(Long userId) {
        return CommentRows.live(11L, UUID.randomUUID(), UUID.randomUUID(), userId, "still here", null, 0);
    }

    private static CommentTreeNode project(CommentRow comment, User author) {
        return CommentTreeNode.fromEntity(
                comment, new CommentTreeNode.Parent.Thread(), author == null
                        ? new CommentTreeNode.Author.Missing()
                        : new CommentTreeNode.Author.Known(UsersApiAdapter.toSummary(author)),
                new Viewer.Guest(), CommentTreeNode.Upvote.NONE, List.of());
    }

    @Test
    void fromEntity_WhenAuthorDeactivatedInGraceWindow_BlanksTheBody() {
        User author = author("GONE");
        TestDataFactory.softDelete(author, Instant.now(), Instant.now().plus(Duration.ofDays(30)));
        CommentRow comment = comment(author.getId());

        CommentTreeNode node = project(comment, author);

        assertThat(node.authorEpithet()).isEqualTo(CommentConstants.DELETED_CONTENT);
        assertThat(node.authorSuffix()).isEqualTo(CommentConstants.DELETED_CONTENT);
        assertThat(node.isDeleted()).as("the comment row itself is not withdrawn").isFalse();
        assertThat(node.content())
                .as("the author-hidden predicate governs the body as it does the name")
                .isEqualTo(CommentConstants.DELETED_CONTENT);
    }

    @Test
    void fromEntity_WhenAuthorReactivated_ServesTheBody() {
        User author = author("GONE");
        TestDataFactory.softDelete(author, Instant.now(), Instant.now().plus(Duration.ofDays(30)));
        CommentRow comment = comment(author.getId());
        project(comment, author);

        TestDataFactory.reactivate(author);
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
        TestDataFactory.softDelete(author, Instant.now(), Instant.now().plus(Duration.ofDays(30)));
        CommentRow comment = CommentRows.deleted(comment(author.getId()), Instant.now());

        CommentTreeNode node = project(comment, author);

        assertThat(node.content()).isEqualTo(CommentConstants.DELETED_CONTENT);
        assertThat(node.isDeleted()).isTrue();
    }
}

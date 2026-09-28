package org.danteplanner.backend.comment.dto;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.shared.util.CommentConstants;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
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
        return User.builder()
                .id(3L)
                .email("gone@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("gone-123")
                .usernameEpithet(epithet)
                .usernameSuffix("gon01")
                .role(UserRole.NORMAL)
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
        return CommentTreeNode.fromEntity(comment, null, author, null, false, List.of());
    }

    @Test
    @DisplayName("a comment by an author deactivated within the grace window is served with an empty body")
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
    @DisplayName("projecting a deactivated author's comment leaves the stored content untouched")
    void fromEntity_WhenAuthorDeactivated_LeavesTheEntityContent() {
        User author = author("GONE");
        author.softDelete(Instant.now().plus(Duration.ofDays(30)));
        PlannerComment comment = comment(author.getId());

        project(comment, author);

        assertThat(comment.getContent()).isEqualTo("still here");
    }

    @Test
    @DisplayName("reactivating the author restores the body")
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
    @DisplayName("a comment whose author is missing is served with an empty body")
    void fromEntity_WhenAuthorMissing_BlanksTheBody() {
        CommentTreeNode node = project(comment(3L), null);

        assertThat(node.content()).isEqualTo(CommentConstants.DELETED_CONTENT);
        assertThat(node.authorEpithet()).isEqualTo(CommentConstants.DELETED_CONTENT);
    }

    @Test
    @DisplayName("a live author without an epithet has the name masked but the body served")
    void fromEntity_WhenLiveAuthorHasNoEpithet_ServesTheBody() {
        User author = author(null);

        CommentTreeNode node = project(comment(author.getId()), author);

        assertThat(node.authorEpithet()).isEqualTo(CommentConstants.DELETED_CONTENT);
        assertThat(node.content()).isEqualTo("still here");
    }

    @Test
    @DisplayName("a deleted comment by a deactivated author is served with an empty body")
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

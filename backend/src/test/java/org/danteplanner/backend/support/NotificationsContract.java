package org.danteplanner.backend.support;

import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.notification.api.NotificationOutcome;
import org.danteplanner.backend.notification.api.NotificationType;
import org.danteplanner.backend.notification.api.Notifications;
import org.danteplanner.backend.user.entity.User;

import static org.assertj.core.api.Assertions.assertThat;

public interface NotificationsContract {

    long UNKNOWN_USER_ID = Long.MAX_VALUE;
    long COMMENT_ID = 41L;
    long REPLY_ID = 42L;
    String TITLE = "Contract Planner";

    Notifications notifications();

    User persisted(User user);

    default <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }

    @Test
    default void notifyCommentReceived_WhenTheRecipientIsLive_DeliversThePayloadItStores() {
        User owner = persisted(UsersContract.newUser().build());
        UUID plannerId = UUID.randomUUID();
        UUID commentPublicId = UUID.randomUUID();

        NotificationOutcome outcome = inTransaction(() -> notifications().notifyCommentReceived(
                COMMENT_ID, commentPublicId, plannerId, TITLE, "<p>A comment</p>", owner.getId()));

        assertThat(outcome).isInstanceOfSatisfying(NotificationOutcome.Delivered.class, delivered -> {
            assertThat(delivered.userId()).isEqualTo(owner.getId());
            assertThat(delivered.payload().id()).isNotBlank();
            assertThat(delivered.payload().createdAt()).isNotBlank();
            assertThat(delivered.payload().type()).isEqualTo(NotificationType.COMMENT_RECEIVED.name());
            assertThat(delivered.payload().contentId()).isEqualTo(Long.toString(COMMENT_ID));
            assertThat(delivered.payload().plannerId()).isEqualTo(plannerId.toString());
            assertThat(delivered.payload().plannerTitle()).isEqualTo(TITLE);
            assertThat(delivered.payload().commentSnippet()).isEqualTo("A comment");
            assertThat(delivered.payload().commentPublicId()).isEqualTo(commentPublicId.toString());
        });
    }

    @Test
    default void notifyCommentReceived_WhenTheSameCommentIsNotifiedTwice_SuppressesTheSecond() {
        User owner = persisted(UsersContract.newUser().build());
        UUID plannerId = UUID.randomUUID();
        inTransaction(() -> notifications().notifyCommentReceived(
                COMMENT_ID, UUID.randomUUID(), plannerId, TITLE, "first", owner.getId()));

        NotificationOutcome second = inTransaction(() -> notifications().notifyCommentReceived(
                COMMENT_ID, UUID.randomUUID(), plannerId, TITLE, "second", owner.getId()));

        assertThat(second).isEqualTo(new NotificationOutcome.Suppressed(
                owner.getId(), Long.toString(COMMENT_ID), NotificationType.COMMENT_RECEIVED));
    }

    @Test
    default void notifyCommentReceived_WhenTheRecipientIsDeleted_Suppresses() {
        User owner = persisted(UsersContract.deletedUser().build());

        NotificationOutcome outcome = inTransaction(() -> notifications().notifyCommentReceived(
                COMMENT_ID, UUID.randomUUID(), UUID.randomUUID(), TITLE, "A comment", owner.getId()));

        assertThat(outcome).isEqualTo(new NotificationOutcome.Suppressed(
                owner.getId(), Long.toString(COMMENT_ID), NotificationType.COMMENT_RECEIVED));
    }

    @Test
    default void notifyCommentReceived_WhenTheRecipientHasNoUserRow_Suppresses() {
        NotificationOutcome outcome = inTransaction(() -> notifications().notifyCommentReceived(
                COMMENT_ID, UUID.randomUUID(), UUID.randomUUID(), TITLE, "A comment", UNKNOWN_USER_ID));

        assertThat(outcome).isEqualTo(new NotificationOutcome.Suppressed(
                UNKNOWN_USER_ID, Long.toString(COMMENT_ID), NotificationType.COMMENT_RECEIVED));
    }

    @Test
    default void notifyReplyReceived_WhenTheRecipientIsLive_DeliversAReply() {
        User author = persisted(UsersContract.newUser().build());

        NotificationOutcome outcome = inTransaction(() -> notifications().notifyReplyReceived(
                REPLY_ID, UUID.randomUUID(), UUID.randomUUID(), TITLE, "A reply", author.getId()));

        assertThat(outcome).isInstanceOfSatisfying(NotificationOutcome.Delivered.class, delivered -> {
            assertThat(delivered.payload().type()).isEqualTo(NotificationType.REPLY_RECEIVED.name());
            assertThat(delivered.payload().contentId()).isEqualTo(Long.toString(REPLY_ID));
        });
    }

    @Test
    default void notifyPlannerRecommended_WhenTheRecipientIsLive_DeliversWithoutCommentFields() {
        User owner = persisted(UsersContract.newUser().build());
        UUID plannerId = UUID.randomUUID();

        NotificationOutcome outcome = inTransaction(() -> notifications().notifyPlannerRecommended(
                plannerId, TITLE, owner.getId()));

        assertThat(outcome).isInstanceOfSatisfying(NotificationOutcome.Delivered.class, delivered -> {
            assertThat(delivered.payload().type()).isEqualTo(NotificationType.PLANNER_RECOMMENDED.name());
            assertThat(delivered.payload().contentId()).isEqualTo(plannerId.toString());
            assertThat(delivered.payload().commentSnippet()).isNull();
            assertThat(delivered.payload().commentPublicId()).isNull();
        });
    }

    @Test
    default void notifyPlannerRecommended_WhenTheTitleIsLong_TruncatesIt() {
        User owner = persisted(UsersContract.newUser().build());
        String title = "t".repeat(150);

        NotificationOutcome outcome = inTransaction(() -> notifications().notifyPlannerRecommended(
                UUID.randomUUID(), title, owner.getId()));

        assertThat(outcome).isInstanceOfSatisfying(NotificationOutcome.Delivered.class, delivered ->
                assertThat(delivered.payload().plannerTitle()).isEqualTo("t".repeat(97) + "..."));
    }

    @Test
    default void notifyPlannerRecommended_WhenTheRecipientIsDeleted_Suppresses() {
        User owner = persisted(UsersContract.deletedUser().build());
        UUID plannerId = UUID.randomUUID();

        NotificationOutcome outcome = inTransaction(() -> notifications().notifyPlannerRecommended(
                plannerId, TITLE, owner.getId()));

        assertThat(outcome).isEqualTo(new NotificationOutcome.Suppressed(
                owner.getId(), plannerId.toString(), NotificationType.PLANNER_RECOMMENDED));
    }
}

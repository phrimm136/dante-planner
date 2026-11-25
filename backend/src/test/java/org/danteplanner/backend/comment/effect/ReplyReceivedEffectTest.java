package org.danteplanner.backend.comment.effect;

import tools.jackson.databind.ObjectMapper;
import org.danteplanner.backend.support.CommentRows;
import org.danteplanner.backend.comment.repository.CommentRow;
import org.danteplanner.backend.support.TestJsonMappers;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.planner.api.PlannerNotificationTarget;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.freshness.FreshnessDeclaration;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEventPayloadReader;
import org.danteplanner.backend.shared.outbox.service.EffectPushQueue;
import org.danteplanner.backend.shared.sse.SseChannel;
import org.danteplanner.backend.shared.sse.SseEnvelope;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.support.FakePublishedPlanners;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.backend.support.RecordingNotifications;
import org.danteplanner.backend.support.RecordingNotifications.ReplyReceived;
import org.danteplanner.backend.support.RecordingSseChannelSender;
import org.danteplanner.backend.support.UsersContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

/**
 * Who hears about a reply, on the same terms as {@link CommentReceivedEffectTest}: the recipient is
 * the parent's author, and the thread push happens either way.
 */
@ExtendWith(MockitoExtension.class)
class ReplyReceivedEffectTest {

    private static final UUID PLANNER_ID = UUID.randomUUID();
    private static final UUID PARENT_PUBLIC_ID = UUID.randomUUID();
    private static final Long OWNER_ID = 1L;
    private static final Long REPLIER_ID = 2L;
    private static final Long PARENT_AUTHOR_ID = 3L;
    private static final Long PARENT_ID = 50L;
    private static final Long REPLY_ID = 101L;

    @Mock
    private PlannerCommentRepository commentRepository;

    private final FakeUsers users = new FakeUsers();

    private final FakePublishedPlanners publishedPlanners = new FakePublishedPlanners();

    private final RecordingNotifications notifications = new RecordingNotifications(users);

    private final RecordingSseChannelSender sseChannel = new RecordingSseChannelSender();

    private final SsePublisher ssePublisher =
            new SsePublisher(sseChannel, TestJsonMappers.springDefaults(), new SimpleMeterRegistry());

    private ReplyReceivedEffect effect;

    @BeforeEach
    void setUp() {
        effect = new ReplyReceivedEffect(commentRepository,
                new CommentQueryService(commentRepository, null, users, null, null),
                publishedPlanners, notifications,
                new DomainEventPayloadReader(new ObjectMapper()));
        publishedPlanners.add(new PlannerNotificationTarget(PLANNER_ID, "Test Planner", OWNER_ID, true), true);
        users.add(UsersContract.newUser().id(OWNER_ID).build());
        users.add(UsersContract.newUser().id(PARENT_AUTHOR_ID).build());
    }

    @Test
    void type_WhenAsked_IsReplyReceived() {
        assertThat(effect.type()).isEqualTo(DomainEventType.REPLY_RECEIVED);
    }

    @Test
    void applyEffect_WhenTheParentAuthorIsEligible_NotifiesAndPushesBoth() {
        CommentRow reply = givenAReplyTo(givenAParent(PARENT_AUTHOR_ID, true));

        applyAndFlush();

        assertThat(notifications.calls()).containsExactly(new ReplyReceived(
                REPLY_ID, reply.publicId(), PLANNER_ID, "Test Planner", "A reply", PARENT_AUTHOR_ID));
        assertThat(sseChannel.envelopes(SseChannel.USER))
                .extracting(SseEnvelope.Wire::type, SseEnvelope.Wire::userId)
                .containsExactly(tuple(SseEventType.NOTIFY_COMMENT, PARENT_AUTHOR_ID));
        assertThat(sseChannel.envelopes(SseChannel.COMMENT))
                .extracting(SseEnvelope.Wire::type, SseEnvelope.Wire::plannerId, SseEnvelope.Wire::excludeUserId)
                .containsExactly(tuple(SseEventType.COMMENT_ADDED, PLANNER_ID.toString(), REPLIER_ID));
    }

    @Test
    void applyEffect_WhenTheReplierWroteTheParent_NotifiesNobodyButStillPushesTheThread() {
        givenAReplyTo(givenAParent(REPLIER_ID, true));

        applyAndFlush();

        assertThat(notifications.calls()).isEmpty();
        assertThat(sseChannel.envelopes(SseChannel.USER)).isEmpty();
        assertThat(sseChannel.envelopes(SseChannel.COMMENT))
                .extracting(SseEnvelope.Wire::type, SseEnvelope.Wire::plannerId, SseEnvelope.Wire::excludeUserId)
                .containsExactly(tuple(SseEventType.COMMENT_ADDED, PLANNER_ID.toString(), REPLIER_ID));
    }

    @Test
    void applyEffect_WhenTheParentAuthorDisabledNotifications_NotifiesNobodyButStillPushesTheThread() {
        givenAReplyTo(givenAParent(PARENT_AUTHOR_ID, false));

        applyAndFlush();

        assertThat(notifications.calls()).isEmpty();
        assertThat(sseChannel.envelopes(SseChannel.USER)).isEmpty();
        assertThat(sseChannel.envelopes(SseChannel.COMMENT))
                .extracting(SseEnvelope.Wire::type, SseEnvelope.Wire::plannerId, SseEnvelope.Wire::excludeUserId)
                .containsExactly(tuple(SseEventType.COMMENT_ADDED, PLANNER_ID.toString(), REPLIER_ID));
    }

    @Test
    void applyEffect_WhenTheReplyWasWithdrawn_AnnouncesNothing() {
        CommentRow reply = CommentRows.deleted(reply(PLANNER_ID, PARENT_ID), Instant.now());
        when(commentRepository.findRowById(REPLY_ID)).thenReturn(Optional.of(reply));

        assertLogs("Reply 101 was withdrawn before it was announced");

        assertThat(notifications.calls()).isEmpty();
        assertThat(sseChannel.attempts()).isEmpty();
    }

    @Test
    void applyEffect_WhenTheReplyIsGone_AnnouncesNothing() {
        when(commentRepository.findRowById(REPLY_ID)).thenReturn(Optional.empty());

        assertLogs("Reply 101 is gone before it was announced");

        assertThat(notifications.calls()).isEmpty();
        assertThat(sseChannel.attempts()).isEmpty();
    }

    @Test
    void applyEffect_WhenItPushesTheThreadInsideAWindow_DeclaresThePlanner() {
        givenAReplyTo(givenAParent(REPLIER_ID, true));
        FreshnessDeclarations.begin();
        try {
            applyAndFlush();

            assertThat(FreshnessDeclarations.declaration())
                    .isEqualTo(new FreshnessDeclaration.Scopes(Set.of(new FreshnessScope.Planner(PLANNER_ID))));
        } finally {
            FreshnessDeclarations.clear();
        }
    }

    @Test
    void applyEffect_WhenTheNotificationIsSuppressed_PushesOnlyTheThread() {
        givenAReplyTo(givenAParent(PARENT_AUTHOR_ID, true));

        applyAndFlush();
        applyAndFlush();

        assertThat(notifications.calls()).hasSize(2);
        assertThat(sseChannel.envelopes(SseChannel.USER)).hasSize(1);
        assertThat(sseChannel.envelopes(SseChannel.COMMENT)).hasSize(2);
    }

    @Test
    void applyEffect_WhenTheParentIsKnown_PushesItsPublicId() {
        givenAReplyTo(givenAParent(PARENT_AUTHOR_ID, true));

        applyAndFlush();

        assertPushedParent(PARENT_PUBLIC_ID.toString());
    }

    @Test
    void applyEffect_WhenTheParentRowIsGone_NotifiesNobodyAndPushesNoParent() {
        givenAReplyTo(parent(PARENT_AUTHOR_ID, true));
        when(commentRepository.findRowById(PARENT_ID)).thenReturn(Optional.empty());

        applyAndFlush();

        assertThat(notifications.calls()).isEmpty();
        assertPushedParent(null);
    }

    @Test
    void applyEffect_WhenTheReplyHasNoParent_NotifiesNobodyAndPushesNoParent() {
        givenTheReply(reply(PLANNER_ID, null));

        applyAndFlush();

        assertThat(notifications.calls()).isEmpty();
        assertPushedParent(null);
    }

    @Test
    void applyEffect_WhenThePlannerIsGone_AnnouncesNothing() {
        UUID gonePlannerId = UUID.randomUUID();
        givenTheReply(reply(gonePlannerId, PARENT_ID));

        assertLogs("Planner " + gonePlannerId + " is gone before reply 101 was announced");
        assertThat(notifications.calls()).isEmpty();
        assertThat(sseChannel.attempts()).isEmpty();
    }

    @Test
    void applyEffect_WhenThePlannerIsUnpublished_LogsThatTheThreadMissedIt() {
        givenAReplyTo(givenAParent(PARENT_AUTHOR_ID, true));
        publishedPlanners.add(new PlannerNotificationTarget(PLANNER_ID, "Test Planner", OWNER_ID, true), false);

        assertLogs("Planner " + PLANNER_ID + " left public view before reply 101 was announced to its thread");
    }

    private void assertPushedParent(String parentPublicId) {
        assertThat(sseChannel.envelopes(SseChannel.COMMENT)).singleElement().satisfies(envelope ->
                assertThat(((Map<?, ?>) envelope.payload()).get("parentCommentId")).isEqualTo(parentPublicId));
    }

    private void assertLogs(String message) {
        assertThat(LogCapture.during(this::applyAndFlush, Level.INFO, ReplyReceivedEffect.class))
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(message);
    }

    /**
     * Runs the arm and releases its queue, which is what the dispatch commit does in production.
     */
    private void applyAndFlush() {
        EffectPushQueue pushes = new EffectPushQueue(ssePublisher);
        effect.applyEffect(event(), pushes);
        pushes.flush();
    }

    private static CommentRow parent(Long authorId, boolean authorNotificationsEnabled) {
        CommentRow parent = CommentRows.live(PARENT_ID, PARENT_PUBLIC_ID, PLANNER_ID, authorId, "Parent", null, 0);
        return authorNotificationsEnabled ? parent : CommentRows.muted(parent);
    }

    private CommentRow givenAParent(Long authorId, boolean authorNotificationsEnabled) {
        CommentRow parent = parent(authorId, authorNotificationsEnabled);
        when(commentRepository.findRowById(PARENT_ID)).thenReturn(Optional.of(parent));
        return parent;
    }

    private CommentRow givenAReplyTo(CommentRow parent) {
        return givenTheReply(reply(PLANNER_ID, parent.id()));
    }

    private CommentRow givenTheReply(CommentRow reply) {
        when(commentRepository.findRowById(REPLY_ID)).thenReturn(Optional.of(reply));
        return reply;
    }

    private static CommentRow reply(UUID plannerId, Long parentId) {
        return CommentRows.live(REPLY_ID, UUID.randomUUID(), plannerId, REPLIER_ID, "A reply", parentId, 1);
    }

    private static DomainEvent event() {
        return DomainEvent.of(DomainEventType.REPLY_RECEIVED, PLANNER_ID,
                "{\"replyId\":" + REPLY_ID + "}");
    }
}

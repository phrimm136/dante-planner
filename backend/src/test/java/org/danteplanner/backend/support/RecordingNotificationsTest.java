package org.danteplanner.backend.support;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.QueryTimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.planner.effect.PlannerRecommendedEffect;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PublishedPlannerRow;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEventPayloadReader;
import org.danteplanner.backend.shared.outbox.service.EffectPushQueue;
import org.danteplanner.backend.shared.sse.SseChannel;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.support.RecordingNotifications.Failed;
import org.danteplanner.backend.support.RecordingNotifications.Operation;
import org.danteplanner.backend.support.RecordingNotifications.PlannerRecommended;
import org.danteplanner.backend.user.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@ExtendWith(ScriptedFailuresCheck.class)
class RecordingNotificationsTest {

    private static final UUID PLANNER_ID = UUID.randomUUID();
    private static final long OWNER_ID = 7L;
    private static final String TITLE = "Recommended Planner";

    @Mock
    private PlannerRepository plannerRepository;

    private final FakeUsers users = new FakeUsers();
    private final RecordingNotifications notifications = new RecordingNotifications(users);
    private final RecordingSseChannelSender sender = new RecordingSseChannelSender();
    private User owner;

    @BeforeEach
    void setUp() {
        owner = users.add(TestDataFactory.unsavedUser(OWNER_ID));
        Planner planner = TestDataFactory.planner(owner).id(PLANNER_ID).title(TITLE).build();
        when(plannerRepository.findPublishedAggregate(PLANNER_ID))
                .thenReturn(Optional.of(new PublishedPlannerRow(planner, TestDataFactory.profileOf(owner))));
    }

    @Test
    void calls_WhenTheRealRecommendationEffectRuns_RecordTheNotificationItDelivers() {
        recommend();

        assertThat(notifications.calls()).containsExactly(new PlannerRecommended(PLANNER_ID, TITLE, OWNER_ID));
        assertThat(sender.envelopes(SseChannel.USER)).singleElement().satisfies(envelope -> {
            assertThat(envelope.type()).isEqualTo(SseEventType.NOTIFY_RECOMMENDED);
            assertThat(envelope.userId()).isEqualTo(OWNER_ID);
        });
    }

    @Test
    void calls_WhenTheSameRecommendationIsRaisedTwice_SuppressTheSecondDelivery() {
        recommend();
        recommend();

        assertThat(notifications.calls()).hasSize(2);
        assertThat(sender.envelopes(SseChannel.USER)).hasSize(1);
    }

    @Test
    void calls_WhenTheOwnerHasDeletedTheAccount_RecordTheCallButPushNothing() {
        owner.softDelete(Instant.now().plus(Duration.ofDays(30)));

        recommend();

        assertThat(notifications.calls()).containsExactly(new PlannerRecommended(PLANNER_ID, TITLE, OWNER_ID));
        assertThat(sender.envelopes(SseChannel.USER)).isEmpty();
    }

    @Test
    void failNext_WhenScripted_FailsOneCallAndThenRecovers() {
        QueryTimeoutException down = new QueryTimeoutException("down");
        notifications.failNext(Operation.PLANNER_RECOMMENDED, down);

        assertThatThrownBy(this::recommend).isInstanceOf(QueryTimeoutException.class).hasMessage("down");
        recommend();

        assertThat(notifications.calls()).containsExactly(
                new Failed(Operation.PLANNER_RECOMMENDED, down), new PlannerRecommended(PLANNER_ID, TITLE, OWNER_ID));
        assertThat(sender.envelopes(SseChannel.USER)).hasSize(1);
    }

    private void recommend() {
        EffectPushQueue pushes = new EffectPushQueue(
                new SsePublisher(sender, new ObjectMapper(), new SimpleMeterRegistry()));
        new PlannerRecommendedEffect(plannerRepository, notifications, new DomainEventPayloadReader(new ObjectMapper()))
                .applyEffect(DomainEvent.of(DomainEventType.PLANNER_RECOMMENDED, PLANNER_ID,
                        "{\"ownerId\":" + OWNER_ID + "}"), pushes);
        pushes.flush();
    }
}

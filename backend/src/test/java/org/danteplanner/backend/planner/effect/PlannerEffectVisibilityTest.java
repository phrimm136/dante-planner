package org.danteplanner.backend.planner.effect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.notification.service.NotificationDispatchService;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEventPayloadReader;
import org.danteplanner.backend.shared.outbox.service.EffectPushQueue;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A planner arm announces a public planner or nothing.
 *
 * <p>The event row records that a planner was published, or crossed the recommendation threshold,
 * at the moment its transaction committed. Both arms re-read through the published predicate rather
 * than by id, because between the commit and the dispatch an owner or a moderator can withdraw the
 * planner — and announcing it then would broadcast a planner nobody can open.</p>
 */
@ExtendWith(MockitoExtension.class)
class PlannerEffectVisibilityTest {

    private static final UUID PLANNER_ID = UUID.randomUUID();

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @Mock
    private SsePublisher ssePublisher;

    private final DomainEventPayloadReader payloads =
            new DomainEventPayloadReader(new ObjectMapper());

    private EffectPushQueue pushes;

    @BeforeEach
    void setUp() {
        pushes = new EffectPushQueue(ssePublisher);
        when(plannerRepository.findPublishedAggregate(PLANNER_ID)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("a planner withdrawn before dispatch is not announced as published")
    void publishedEffect_WhenThePlannerIsNoLongerPublished_AnnouncesNothing() {
        PlannerPublishedEffect effect = new PlannerPublishedEffect(
                plannerRepository, notificationDispatchService, payloads);

        effect.applyEffect(DomainEvent.of(DomainEventType.PLANNER_PUBLISHED, PLANNER_ID,
                "{\"authorId\":7}"), pushes);
        pushes.flush();

        verifyNoInteractions(notificationDispatchService, ssePublisher);
    }

    @Test
    @DisplayName("a planner withdrawn before dispatch is not announced as recommended")
    void recommendedEffect_WhenThePlannerIsNoLongerPublished_AnnouncesNothing() {
        PlannerRecommendedEffect effect = new PlannerRecommendedEffect(
                plannerRepository, notificationDispatchService, payloads);

        effect.applyEffect(DomainEvent.of(DomainEventType.PLANNER_RECOMMENDED, PLANNER_ID,
                "{\"ownerId\":7}"), pushes);
        pushes.flush();

        verifyNoInteractions(notificationDispatchService, ssePublisher);
    }
}

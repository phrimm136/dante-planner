package org.danteplanner.backend.planner.effect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.notification.api.NotificationOutcome;
import org.danteplanner.backend.notification.api.Notifications;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PublishedPlannerRow;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEffect;
import org.danteplanner.backend.shared.outbox.service.DomainEventPayloadReader;
import org.danteplanner.backend.shared.outbox.service.EffectPushQueue;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlannerRecommendedEffect implements DomainEffect {

    private final PlannerRepository plannerRepository;
    private final Notifications notifications;
    private final DomainEventPayloadReader payloads;

    @Override
    public DomainEventType type() {
        return DomainEventType.PLANNER_RECOMMENDED;
    }

    @Override
    public void applyEffect(DomainEvent event, EffectPushQueue pushes) {
        Optional<PublishedPlannerRow> found = plannerRepository.findPublishedAggregate(event.getAggregateId());
        if (found.isEmpty()) {
            log.info("Planner {} is no longer published; its recommendation goes unannounced",
                    event.getAggregateId());
            return;
        }

        Planner planner = found.get().planner();
        long ownerId = payloads.requireId(event, "ownerId");
        NotificationOutcome outcome = notifications.notifyPlannerRecommended(
                planner.getId(), planner.getTitle(), ownerId);

        if (outcome instanceof NotificationOutcome.Delivered delivered) {
            pushes.userEvent(delivered.userId(), SseEventType.NOTIFY_RECOMMENDED,
                    delivered.payload().id(), delivered.payload());
        }
    }
}

package org.danteplanner.backend.planner.effect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.notification.service.NotificationDispatchService;
import org.danteplanner.backend.planner.dto.PlannerPublishedPayload;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEffect;
import org.danteplanner.backend.shared.outbox.service.DomainEventPayloadReader;
import org.danteplanner.backend.shared.outbox.service.EffectPushQueue;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Announces a first publication: one fan-out of notification rows, then the site-wide broadcast.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlannerPublishedEffect implements DomainEffect {

    private final PlannerRepository plannerRepository;
    private final NotificationDispatchService notificationDispatchService;
    private final DomainEventPayloadReader payloads;

    @Override
    public DomainEventType type() {
        return DomainEventType.PLANNER_PUBLISHED;
    }

    @Override
    public void applyEffect(DomainEvent event, EffectPushQueue pushes) {
        // The predicate of the raise site, not merely the row: a planner withdrawn between the vote
        // to publish and this dispatch has nothing left to announce.
        Optional<Planner> found = plannerRepository.findPublishedAggregate(event.getAggregateId());
        if (found.isEmpty()) {
            log.info("Planner {} is no longer published; its publication goes unannounced",
                    event.getAggregateId());
            return;
        }

        Planner planner = found.get();
        long authorId = payloads.requireId(event, "authorId");
        notificationDispatchService.notifyPlannerPublished(authorId, planner.getId(), planner.getTitle());
        pushes.broadcast(authorId, SseEventType.NOTIFY_PUBLISHED,
                PlannerPublishedPayload.fromEntity(planner));
    }
}

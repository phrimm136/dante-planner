package org.danteplanner.backend.shared.outbox.service;

import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes a committed event to the arm that answers for its type.
 *
 * <p>Two arms declaring the same type fail the context at startup rather than letting one silently
 * shadow the other.</p>
 */
@Component
public class DomainEffectRegistry {

    private final Map<DomainEventType, DomainEffect> effectsByType;

    public DomainEffectRegistry(List<DomainEffect> effects) {
        this.effectsByType = effects.stream()
                .collect(Collectors.toUnmodifiableMap(DomainEffect::type, Function.identity()));
    }

    /**
     * Apply the arm registered for this event's type.
     *
     * @param event  the event being dispatched
     * @param pushes the queue the arm enqueues its announcements on
     * @throws IllegalStateException if no arm answers for the event's type
     */
    public void applyEffectFor(DomainEvent event, EffectPushQueue pushes) {
        Optional.ofNullable(effectsByType.get(event.getEventType()))
                .orElseThrow(() -> new IllegalStateException(
                        "no effect arm for " + event.getEventType()))
                .applyEffect(event, pushes);
    }
}

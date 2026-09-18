package org.danteplanner.backend.shared.outbox.service;

import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DomainEffectRegistry {

    private final Map<DomainEventType, DomainEffect> effectsByType;

    public DomainEffectRegistry(List<DomainEffect> effects) {
        this.effectsByType = effects.stream()
                .collect(Collectors.toUnmodifiableMap(DomainEffect::type, Function.identity()));
    }

    public void applyEffectFor(DomainEvent event, EffectPushQueue pushes) {
        Optional.ofNullable(effectsByType.get(event.getEventType()))
                .orElseThrow(() -> new IllegalStateException(
                        "no effect arm for " + event.getEventType()))
                .applyEffect(event, pushes);
    }
}

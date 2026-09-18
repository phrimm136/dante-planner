package org.danteplanner.backend.shared.outbox.service;

import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;

public interface DomainEffect {

    DomainEventType type();

    void applyEffect(DomainEvent event, EffectPushQueue pushes);
}

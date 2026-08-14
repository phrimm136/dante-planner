package org.danteplanner.backend.shared.outbox.service;

import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;

/**
 * One arm of the outbox: everything that has to happen because an event of a given type committed.
 *
 * <p>An arm runs inside the dispatcher's transaction and owns the whole effect — the rows it
 * derives and the pushes that announce them. It re-reads what it needs from the ids the event
 * carries.</p>
 */
public interface DomainEffect {

    /**
     * The event type this arm answers for.
     *
     * @return the type, unique across every registered arm
     */
    DomainEventType type();

    /**
     * Apply the effect of one committed event.
     *
     * @param event the event being dispatched
     */
    void applyEffect(DomainEvent event);
}

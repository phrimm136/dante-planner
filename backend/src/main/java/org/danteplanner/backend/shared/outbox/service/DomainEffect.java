package org.danteplanner.backend.shared.outbox.service;

import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;

/**
 * One arm of the outbox: everything that has to happen because an event of a given type committed.
 *
 * <p>An arm runs inside the dispatcher's transaction and owns the whole effect — the rows it
 * derives and the pushes that announce them. It re-reads what it needs from the ids the event
 * carries, applying the raise site's own predicate rather than mere existence, and it enqueues its
 * announcements rather than sending them: a push sent here would name a row the dispatch might
 * still roll back.</p>
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
     * @param event  the event being dispatched
     * @param pushes the queue announcements are enqueued on, released after the dispatch commits
     */
    void applyEffect(DomainEvent event, EffectPushQueue pushes);
}

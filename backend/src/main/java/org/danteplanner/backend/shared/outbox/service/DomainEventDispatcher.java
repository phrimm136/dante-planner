package org.danteplanner.backend.shared.outbox.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies one committed event's effect and closes the row in the same transaction.
 *
 * <p>Idempotent by construction: the row is read under a write lock and skipped when it already
 * carries a dispatch, so the eager hop and the relay racing on the same id derive the effect once.
 * The arm's writes and the {@code dispatched_at} stamp commit together, which is what lets a
 * failed dispatch be retried without leaving half an effect behind.</p>
 */
@Service
@RequiredArgsConstructor
public class DomainEventDispatcher {

    private final DomainEventRepository events;
    private final DomainEffectRegistry effectRegistry;

    /**
     * Derive the effect of one event, unless another dispatch already did.
     *
     * @param eventId the event to dispatch
     */
    @Transactional
    public void dispatchDomainEvent(long eventId) {
        events.findForDispatch(eventId)
                .filter(event -> !event.isDispatched())
                .ifPresent(event -> {
                    event.recordAttempt();
                    effectRegistry.applyEffectFor(event);
                    event.markDispatched();
                });
    }
}

package org.danteplanner.backend.shared.outbox.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.outbox.config.OutboxAsyncConfig;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Dispatches a recorded event as soon as its transaction commits.
 *
 * <p>Latency only. Every failure here is swallowed because the row is still open and the relay
 * will find it; what this hop buys is that the common case does not wait for the next tick.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventEagerDispatch {

    private final DomainEventDispatcher dispatcher;

    /**
     * Dispatch one committed event off the request thread.
     *
     * @param event the event that just committed
     */
    @Async(OutboxAsyncConfig.OUTBOX_DISPATCH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDomainEventRecorded(DomainEventRecorded event) {
        try {
            dispatcher.dispatchDomainEvent(event.eventId());
        } catch (RuntimeException e) {
            log.error("Eager dispatch of domain event {} failed; relay will retry", event.eventId(), e);
        }
    }
}

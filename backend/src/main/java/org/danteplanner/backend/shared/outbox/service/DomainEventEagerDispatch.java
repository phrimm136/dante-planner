package org.danteplanner.backend.shared.outbox.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.outbox.config.OutboxAsyncConfig;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventEagerDispatch {

    private final DomainEventDispatcher dispatcher;

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

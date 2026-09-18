package org.danteplanner.backend.shared.outbox.service;

import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.outbox.config.OutboxConstants;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DomainEventDispatcher {

    private final DomainEventRepository events;
    private final DomainEffectRegistry effectRegistry;
    private final DomainEventAttemptRecorder attemptRecorder;
    private final SsePublisher ssePublisher;

    @Transactional
    public void dispatchDomainEvent(long eventId) {
        // The connection is lazy: nothing is drawn from the pool until the first statement runs, so
        // the recorder's own transaction does not open a second connection behind one this
        // transaction already holds.
        int attempts = attemptRecorder.recordAttempt(eventId);

        try {
            events.findForDispatch(eventId)
                    .filter(event -> !event.isDispatched())
                    .ifPresent(event -> {
                        EffectPushQueue pushes = new EffectPushQueue(ssePublisher);
                        effectRegistry.applyEffectFor(event, pushes);
                        event.markDispatched();
                        TransactionSynchronizationManager.registerSynchronization(
                                new EffectPushSynchronization(pushes));
                    });
        } catch (RuntimeException e) {
            alarmIfSpent(eventId, attempts, e);
            throw e;
        }
    }

    private void alarmIfSpent(long eventId, int attempts, RuntimeException cause) {
        if (attempts != OutboxConstants.DISPATCH_ATTEMPT_CAP) {
            return;
        }
        log.error("Domain event {} spent its last dispatch attempt and will not be relayed again; "
                + "its effect was never derived", eventId, cause);
        Sentry.captureException(cause);
    }

    @Transactional
    public List<Long> pendingEventIds(Instant cutoff, int batchSize) {
        return events.undispatchedIdsOlderThan(
                cutoff, OutboxConstants.DISPATCH_ATTEMPT_CAP, batchSize);
    }
}

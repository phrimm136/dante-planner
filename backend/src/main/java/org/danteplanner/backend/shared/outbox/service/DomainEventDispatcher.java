package org.danteplanner.backend.shared.outbox.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.outbox.config.OutboxConstants;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

/**
 * Applies one committed event's effect and closes the row in the same transaction.
 *
 * <p>Idempotent by construction: the row is read under a write lock and skipped when it already
 * carries a dispatch, so the eager hop and the relay racing on the same id derive the effect once.
 * The arm's writes and the {@code dispatched_at} stamp commit together, which is what lets a failed
 * dispatch be retried without leaving half an effect behind.</p>
 *
 * <p>Nothing is announced from inside that transaction. The arm enqueues its pushes and they leave
 * the process only once the commit is durable, so a dispatch that rolls back announces nothing for
 * a client to cache against a row that does not exist.</p>
 */
@Service
@RequiredArgsConstructor
public class DomainEventDispatcher {

    private final DomainEventRepository events;
    private final DomainEffectRegistry effectRegistry;
    private final DomainEventAttemptRecorder attemptRecorder;
    private final SsePublisher ssePublisher;

    /**
     * Derive the effect of one event, unless another dispatch already did.
     *
     * @param eventId the event to dispatch
     */
    @Transactional
    public void dispatchDomainEvent(long eventId) {
        // Before the row lock, not after: the lock this transaction is about to take is the one the
        // recorder's own transaction would then be waiting for.
        attemptRecorder.recordAttempt(eventId);

        events.findForDispatch(eventId)
                .filter(event -> !event.isDispatched())
                .ifPresent(event -> {
                    EffectPushQueue pushes = new EffectPushQueue(ssePublisher);
                    effectRegistry.applyEffectFor(event, pushes);
                    event.markDispatched();
                    TransactionSynchronizationManager.registerSynchronization(
                            new EffectPushSynchronization(pushes));
                });
    }

    /**
     * The events the relay should try next.
     *
     * <p>Read-write rather than {@code readOnly}, which is this tree's replica-routing signal: a
     * recovery scan is the last read that may run against a lagging replica, because a row the
     * replica has not seen is a row the relay concludes does not need relaying.</p>
     *
     * @param cutoff    the age an event must exceed to be relayed
     * @param batchSize the most ids to return in one pass
     * @return the ids, oldest first
     */
    @Transactional
    public List<Long> pendingEventIds(Instant cutoff, int batchSize) {
        return events.undispatchedIdsOlderThan(
                cutoff, OutboxConstants.DISPATCH_ATTEMPT_CAP, batchSize);
    }
}

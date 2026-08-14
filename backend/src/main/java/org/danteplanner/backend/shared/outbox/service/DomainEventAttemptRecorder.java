package org.danteplanner.backend.shared.outbox.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts dispatch attempts in a transaction of its own.
 *
 * <p>{@code REQUIRES_NEW} is the whole point. An increment written in the dispatch transaction is
 * undone by the very failure it exists to record, so a row that can never be dispatched would be
 * retried on every relay tick forever with its counter frozen at zero. Committing the attempt
 * separately is what lets the relay eventually stop.</p>
 */
@Service
@RequiredArgsConstructor
public class DomainEventAttemptRecorder {

    private final DomainEventRepository events;

    /**
     * Count one attempt against an event, durably.
     *
     * <p>The caller must not already hold the row's lock: this transaction takes it, and a caller
     * holding it would be waiting on a transaction waiting on itself.</p>
     *
     * <p>Counting only: whether the count now means the row is beyond saving is the dispatch's
     * question, and it can only be answered once that dispatch has failed.</p>
     *
     * @param eventId the event about to be dispatched
     * @return the event's attempt count after the increment, zero when no row carries the id
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recordAttempt(long eventId) {
        events.incrementAttempts(eventId);
        return events.attemptsOf(eventId).orElse(0);
    }
}

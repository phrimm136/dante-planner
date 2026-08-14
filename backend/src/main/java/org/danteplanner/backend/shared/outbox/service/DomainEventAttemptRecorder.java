package org.danteplanner.backend.shared.outbox.service;

import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.outbox.config.OutboxConstants;
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
@Slf4j
public class DomainEventAttemptRecorder {

    private final DomainEventRepository events;

    /**
     * Count one attempt against an event, durably.
     *
     * <p>The caller must not already hold the row's lock: this transaction takes it, and a caller
     * holding it would be waiting on a transaction waiting on itself.</p>
     *
     * @param eventId the event about to be dispatched
     * @return the event's attempt count after the increment, zero when no row carries the id
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recordAttempt(long eventId) {
        events.incrementAttempts(eventId);
        int attempts = events.attemptsOf(eventId).orElse(0);

        if (attempts == OutboxConstants.DISPATCH_ATTEMPT_CAP) {
            IllegalStateException poisoned = new IllegalStateException(
                    "Domain event " + eventId + " reached the dispatch attempt cap and will not be "
                            + "relayed again; its effect was never derived");
            log.error(poisoned.getMessage(), poisoned);
            Sentry.captureException(poisoned);
        }
        return attempts;
    }
}

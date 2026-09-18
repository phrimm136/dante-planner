package org.danteplanner.backend.shared.outbox.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DomainEventAttemptRecorder {

    private final DomainEventRepository events;

    /**
     * The caller must not already hold the row's lock: this transaction takes it, and a caller
     * holding it would be waiting on a transaction waiting on itself.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recordAttempt(long eventId) {
        events.incrementAttempts(eventId);
        return events.attemptsOf(eventId).orElse(0);
    }
}

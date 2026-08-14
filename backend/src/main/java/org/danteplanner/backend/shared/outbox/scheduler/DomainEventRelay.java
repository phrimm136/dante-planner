package org.danteplanner.backend.shared.outbox.scheduler;

import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.danteplanner.backend.shared.outbox.service.DomainEventDispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Re-dispatches events the eager hop never closed.
 *
 * <p>This is the recovery half of the outbox and the reason a lost push is a delay rather than a
 * loss: whatever a dead pod left undispatched is found here on the next tick. The grace window
 * keeps the relay off rows the eager hop may still be holding, so the two do not contend for the
 * same row lock in the common case.</p>
 */
@Component
@Slf4j
public class DomainEventRelay {

    private final DomainEventRepository events;
    private final DomainEventDispatcher dispatcher;
    private final Duration graceDuration;
    private final int batchSize;

    public DomainEventRelay(
            DomainEventRepository events,
            DomainEventDispatcher dispatcher,
            @Value("${outbox.relay.grace}") Duration graceDuration,
            @Value("${outbox.relay.batch-size}") int batchSize) {
        this.events = events;
        this.dispatcher = dispatcher;
        this.graceDuration = graceDuration;
        this.batchSize = batchSize;
    }

    /**
     * Dispatch one batch of events the eager hop left open.
     */
    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:60000}")
    @SchedulerLock(name = "dispatchDomainEvents", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void dispatchPendingEvents() {
        Instant cutoff = Instant.now().minus(graceDuration);
        events.undispatchedIdsOlderThan(cutoff, batchSize).forEach(this::relayOne);
    }

    private void relayOne(long eventId) {
        try {
            dispatcher.dispatchDomainEvent(eventId);
        } catch (RuntimeException e) {
            log.error("Relay dispatch of domain event {} failed", eventId, e);
            Sentry.captureException(e);
        }
    }
}

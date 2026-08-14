package org.danteplanner.backend.shared.outbox.scheduler;

import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
 *
 * <p>Each event is dispatched in a transaction of its own, and the scan runs in one more. A single
 * transaction spanning the batch would hold every row it touched until the last one finished, and
 * one poisoned event would take the whole batch down with it.</p>
 *
 * <p>The lease is sized for the degraded case rather than the healthy one: a full batch published
 * through a Redis that is timing out spends its command timeout per push, which runs far past the
 * five minutes a healthy batch needs — and a lease that expires mid-batch puts a second pod on the
 * rows this one is still working through.</p>
 */
@Component
@Slf4j
public class DomainEventRelay {

    private final DomainEventDispatcher dispatcher;
    private final Duration graceDuration;
    private final int batchSize;

    public DomainEventRelay(
            DomainEventDispatcher dispatcher,
            @Value("${outbox.relay.grace}") Duration graceDuration,
            @Value("${outbox.relay.batch-size}") int batchSize) {
        this.dispatcher = dispatcher;
        this.graceDuration = graceDuration;
        this.batchSize = batchSize;
    }

    /**
     * Dispatch one batch of events the eager hop left open.
     */
    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:60000}")
    @SchedulerLock(name = "dispatchDomainEvents", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
    public void dispatchPendingEvents() {
        Instant cutoff = Instant.now().minus(graceDuration);
        dispatcher.pendingEventIds(cutoff, batchSize).forEach(this::relayOne);
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

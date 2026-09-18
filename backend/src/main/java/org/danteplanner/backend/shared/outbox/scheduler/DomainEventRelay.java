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
     * A fixed delay declared without an initial delay runs the instant the context is built.
     */
    @Scheduled(initialDelayString = "${outbox.relay.fixed-delay-ms:60000}",
            fixedDelayString = "${outbox.relay.fixed-delay-ms:60000}")
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

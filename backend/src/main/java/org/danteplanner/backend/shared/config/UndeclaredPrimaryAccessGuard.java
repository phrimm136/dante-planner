package org.danteplanner.backend.shared.config;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.ApplicationListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.boot.context.event.ApplicationReadyEvent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.danteplanner.backend.shared.failure.OnUndeclared;

/**
 * Flyway, Hibernate schema validation and the pool-metrics unwrap all acquire a connection before
 * {@link ApplicationReadyEvent}.
 */
public class UndeclaredPrimaryAccessGuard implements ApplicationListener<ApplicationReadyEvent> {

    static final String UNDECLARED_COUNTER = "datasource.primary.undeclared";

    private final Counter undeclaredCounter;
    private final OnUndeclared onUndeclared;
    private final AtomicBoolean armed = new AtomicBoolean();

    public UndeclaredPrimaryAccessGuard(MeterRegistry meterRegistry, OnUndeclared onUndeclared) {
        this.undeclaredCounter = Counter.builder(UNDECLARED_COUNTER).register(meterRegistry);
        this.onUndeclared = Objects.requireNonNull(onUndeclared, "datasource.routing.on-undeclared-primary");
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        armed.set(true);
    }

    public void checkDeclared() {
        if (!armed.get() || TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        switch (onUndeclared) {
            case FAIL -> throw new IllegalStateException(
                    "PRIMARY connection acquired outside a transaction: declare the caller with "
                            + "@Transactional(readOnly = true) so the read can route to the replica");
            case COUNT -> undeclaredCounter.increment();
        }
    }
}

package org.danteplanner.backend.shared.config;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.ApplicationListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.boot.context.event.ApplicationReadyEvent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Flyway, Hibernate schema validation and the pool-metrics unwrap all acquire a connection before
 * {@link ApplicationReadyEvent}.
 */
public class UndeclaredPrimaryAccessGuard implements ApplicationListener<ApplicationReadyEvent> {

    static final String UNDECLARED_COUNTER = "datasource.primary.undeclared";

    private final Counter undeclaredCounter;
    private final boolean failFast;
    private final AtomicBoolean armed = new AtomicBoolean();

    public UndeclaredPrimaryAccessGuard(MeterRegistry meterRegistry, boolean failFast) {
        this.undeclaredCounter = Counter.builder(UNDECLARED_COUNTER).register(meterRegistry);
        this.failFast = failFast;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        armed.set(true);
    }

    public void checkDeclared() {
        if (!armed.get() || TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        if (failFast) {
            throw new IllegalStateException(
                    "PRIMARY connection acquired outside a transaction: declare the caller with "
                            + "@Transactional(readOnly = true) so the read can route to the replica");
        }
        undeclaredCounter.increment();
    }
}

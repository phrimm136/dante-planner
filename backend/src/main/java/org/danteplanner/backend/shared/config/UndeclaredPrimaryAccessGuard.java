package org.danteplanner.backend.shared.config;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.ApplicationListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.boot.context.event.ApplicationReadyEvent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Watches for a PRIMARY connection acquired while no transaction is active.
 *
 * <p>Such an acquisition is undeclared: it carries no read-only flag, so it cannot be routed to the
 * replica and it holds a primary connection for whatever the caller does next. Under fail-fast the
 * acquisition throws; otherwise it is counted and served.</p>
 *
 * <p>The guard arms at {@link ApplicationReadyEvent}. Flyway, Hibernate schema validation and the
 * pool-metrics unwrap all acquire before that point.</p>
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

    /**
     * Judges one resolved-to-PRIMARY acquisition. Never changes the routing decision.
     *
     * @throws IllegalStateException when fail-fast is on and the acquisition is undeclared
     */
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

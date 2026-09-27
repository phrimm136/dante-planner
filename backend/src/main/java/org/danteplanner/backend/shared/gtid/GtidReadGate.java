package org.danteplanner.backend.shared.gtid;

import lombok.extern.slf4j.Slf4j;
import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Checks whether the local replica has applied a given GTID.
 * Returns {@code true} once the replica has caught up (the WAIT query returned success),
 * {@code false} while the write has not yet replicated.
 *
 * <p>Runs {@code WAIT_FOR_EXECUTED_GTID_SET} inside a read-only transaction on the {@code @Primary}
 * routing datasource, so the probe resolves to the replica pool. Called before the request pins to
 * the primary, so a not-caught-up result lets the filter route this one request to the primary.</p>
 */
@Slf4j
public class GtidReadGate {


    private static final String WAIT_GTID_SQL = "SELECT WAIT_FOR_EXECUTED_GTID_SET(?, ?)";

    /** Probe bound (seconds), not a correctness window or sleep (mechanics §5, INV4). */
    private static final double PROBE_TIMEOUT_SECONDS = 0.05;

    private static final int APPLIED = 0;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate readOnlyTransaction;

    /**
     * Where each gated read was routed, tagged {@code replica} or {@code primary}. A pinned read
     * is correct but pays the cross-region round trip, so watch the primary share, not the
     * absolute.
     */
    private final Counter replicaServed;
    private final Counter primaryPinned;

    public GtidReadGate(DataSource dataSource, MeterRegistry meterRegistry) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.readOnlyTransaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.readOnlyTransaction.setReadOnly(true);
        this.replicaServed = meterRegistry.counter("gtid.gate", "outcome", "replica");
        this.primaryPinned = meterRegistry.counter("gtid.gate", "outcome", "primary");
    }

    public boolean isCaughtUp(String gtid) {
        boolean caughtUp;
        try {
            Integer result = readOnlyTransaction.execute(status ->
                    jdbcTemplate.queryForObject(
                            WAIT_GTID_SQL, Integer.class, gtid, PROBE_TIMEOUT_SECONDS));
            caughtUp = result != null && result == APPLIED;
        } catch (DataAccessException e) {
            log.warn("GTID wait probe failed for gtid={}, routing to primary", gtid, e);
            caughtUp = false;
        }
        (caughtUp ? replicaServed : primaryPinned).increment();
        return caughtUp;
    }
}

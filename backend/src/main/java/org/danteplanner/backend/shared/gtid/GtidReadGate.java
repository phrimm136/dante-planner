package org.danteplanner.backend.shared.gtid;

import lombok.extern.slf4j.Slf4j;
import java.sql.SQLException;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mysql.cj.exceptions.MysqlErrorNumbers;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource;
import org.danteplanner.backend.shared.config.RoutingKey;

/**
 * Checks whether the local replica has applied a given GTID.
 *
 * <p>Runs {@code WAIT_FOR_EXECUTED_GTID_SET} inside a read-only transaction on the {@code @Primary}
 * routing datasource, so the probe resolves to the replica pool. Called before the request pins to
 * the primary, so a not-caught-up result lets the filter route this one request to the primary.</p>
 */
@Slf4j
public class GtidReadGate {


    private static final String WAIT_GTID_SQL = "SELECT WAIT_FOR_EXECUTED_GTID_SET(?, ?)";
    private static final String PRIMARY_EXECUTED_SQL = "SELECT GTID_SUBSET(?, @@GLOBAL.gtid_executed)";

    /** Probe bound (seconds), not a correctness window or sleep (mechanics §5, INV4). */
    private static final double PROBE_TIMEOUT_SECONDS = 0.05;

    private static final int APPLIED = 0;
    private static final int TIMED_OUT = 1;
    private static final int NOT_SUBSET = 0;

    private static final Set<Integer> MALFORMED_GTID_ERRORS = Set.of(
            MysqlErrorNumbers.ER_MALFORMED_GTID_SET_SPECIFICATION,
            MysqlErrorNumbers.ER_MALFORMED_GTID_SET_ENCODING,
            MysqlErrorNumbers.ER_MALFORMED_GTID_SPECIFICATION);

    public enum Verdict {
        CAUGHT_UP,
        NOT_CAUGHT_UP,
        UNUSABLE,
        NEVER_EXECUTED
    }

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

    public Verdict probe(String gtid) {
        Verdict verdict = waitVerdict(gtid);
        boolean pinned = verdict == Verdict.NOT_CAUGHT_UP || verdict == Verdict.NEVER_EXECUTED;
        (pinned ? primaryPinned : replicaServed).increment();
        return verdict;
    }

    private Verdict waitVerdict(String gtid) {
        Integer result;
        try {
            result = readOnlyTransaction.execute(status ->
                    jdbcTemplate.queryForObject(
                            WAIT_GTID_SQL, Integer.class, gtid, PROBE_TIMEOUT_SECONDS));
        } catch (DataAccessException e) {
            if (isMalformedGtid(e)) {
                log.warn("GTID wait rejected gtid={} as malformed, clearing the cookie: {}",
                        gtid, e.getMostSpecificCause().getMessage());
                return Verdict.UNUSABLE;
            }
            log.warn("GTID wait probe failed for gtid={}, routing to primary", gtid, e);
            return Verdict.NOT_CAUGHT_UP;
        }
        if (result != null && result == APPLIED) {
            return Verdict.CAUGHT_UP;
        }
        if (result != null && result == TIMED_OUT && isNeverExecutedOnPrimary(gtid)) {
            return Verdict.NEVER_EXECUTED;
        }
        return Verdict.NOT_CAUGHT_UP;
    }

    private boolean isNeverExecutedOnPrimary(String gtid) {
        ReadOnlyRoutingDataSource.pinTo(RoutingKey.PRIMARY);
        try {
            Integer subset = jdbcTemplate.queryForObject(PRIMARY_EXECUTED_SQL, Integer.class, gtid);
            return subset != null && subset == NOT_SUBSET;
        } catch (DataAccessException e) {
            log.warn("GTID subset check on the primary failed for gtid={}, routing to primary",
                    gtid, e);
            return false;
        } finally {
            ReadOnlyRoutingDataSource.clear();
        }
    }

    private static boolean isMalformedGtid(DataAccessException e) {
        return e.getMostSpecificCause() instanceof SQLException sqlException
                && MALFORMED_GTID_ERRORS.contains(sqlException.getErrorCode());
    }
}

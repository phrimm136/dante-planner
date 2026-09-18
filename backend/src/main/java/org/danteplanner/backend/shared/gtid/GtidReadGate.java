package org.danteplanner.backend.shared.gtid;

import lombok.extern.slf4j.Slf4j;
import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class GtidReadGate {


    private static final String WAIT_GTID_SQL = "SELECT WAIT_FOR_EXECUTED_GTID_SET(?, ?)";

    private static final double PROBE_TIMEOUT_SECONDS = 0.05;

    private static final int APPLIED = 0;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate readOnlyTransaction;

    public GtidReadGate(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.readOnlyTransaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.readOnlyTransaction.setReadOnly(true);
    }

    public boolean isCaughtUp(String gtid) {
        try {
            Integer result = readOnlyTransaction.execute(status ->
                    jdbcTemplate.queryForObject(
                            WAIT_GTID_SQL, Integer.class, gtid, PROBE_TIMEOUT_SECONDS));
            return result != null && result == APPLIED;
        } catch (DataAccessException e) {
            log.warn("GTID wait probe failed for gtid={}, routing to primary", gtid, e);
            return false;
        }
    }
}

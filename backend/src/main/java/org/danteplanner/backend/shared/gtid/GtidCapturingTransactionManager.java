package org.danteplanner.backend.shared.gtid;

import javax.sql.DataSource;

import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.persistence.EntityManagerFactory;

/**
 * A {@link JpaTransactionManager} that registers a {@link GtidCommitSynchronization} for every
 * non-read-only transaction it begins, so the read-your-writes cookie can echo the GTID each write
 * commits.
 *
 * <p>The transaction manager is the one seam every transaction flows through, including each
 * {@code REQUIRES_NEW} listener transaction, so registration here captures the union of a request's
 * commits in commit order without touching any service. Read-only transactions (the replica-routing
 * signal) are skipped — they commit no GTID. The datasource is set on the manager so the
 * synchronization can reach the transaction's bound connection through {@code DataSourceUtils}.</p>
 */
public class GtidCapturingTransactionManager extends JpaTransactionManager {

    private final transient GtidWriteCapture capture;
    private final transient DataSource captureDataSource;

    public GtidCapturingTransactionManager(
            EntityManagerFactory entityManagerFactory, GtidWriteCapture capture, DataSource dataSource) {
        super(entityManagerFactory);
        this.capture = capture;
        this.captureDataSource = dataSource;
        setDataSource(dataSource);
    }

    @Override
    protected void prepareSynchronization(DefaultTransactionStatus status, TransactionDefinition definition) {
        super.prepareSynchronization(status, definition);
        if (status.isNewSynchronization()
                && !definition.isReadOnly()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new GtidCommitSynchronization(capture, captureDataSource));
        }
    }
}

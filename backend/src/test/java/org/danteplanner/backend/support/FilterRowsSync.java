package org.danteplanner.backend.support;

import java.util.UUID;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import org.danteplanner.backend.planner.service.PlannerCatalogService;

public final class FilterRowsSync {

    private FilterRowsSync() {
    }

    public static TransactionTemplate readCommitted(PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction;
    }

    public static void syncFilterRows(PlatformTransactionManager transactionManager,
            PlannerCatalogService catalogService, UUID plannerId) {
        readCommitted(transactionManager).executeWithoutResult(status -> catalogService.syncFilterRows(plannerId));
    }
}

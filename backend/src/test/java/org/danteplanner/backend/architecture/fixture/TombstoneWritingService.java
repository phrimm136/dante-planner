package org.danteplanner.backend.architecture.fixture;

import org.danteplanner.backend.shared.readpath.ContentTombstoneStore;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

public class TombstoneWritingService {

    @Transactional
    public void hideBeforeCommit(ContentTombstoneStore store, UUID plannerId) {
        store.writeTombstone("published-planner", plannerId);
    }

    @Transactional
    public void unhideBeforeCommit(ContentTombstoneStore store, UUID plannerId) {
        store.clearTombstones("published-planner", List.of(plannerId));
    }

    @Transactional
    public void unhideAfterCommit(ContentTombstoneStore store, UUID plannerId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                store.clearTombstones("published-planner", List.of(plannerId));
            }
        });
    }
}

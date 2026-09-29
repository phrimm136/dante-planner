package org.danteplanner.backend.architecture.fixture;

import org.danteplanner.backend.shared.readpath.ContentTombstoneStore;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public class TombstoneWritingService {

    private TombstoneWritingService self;

    @Transactional
    public void hideBeforeCommit(ContentTombstoneStore store, UUID plannerId) {
        store.writeTombstoneOrThrow("published-planner", plannerId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncFromCommittedState(ContentTombstoneStore store, UUID plannerId) {
        store.writeTombstoneOrThrow("published-planner", plannerId);
    }

    public void drainPending(ContentTombstoneStore store, UUID plannerId) {
        self.syncFromCommittedState(store, plannerId);
    }

    public void syncFromElsewhere(ContentTombstoneStore store, UUID plannerId) {
        syncFromCommittedState(store, plannerId);
    }
}

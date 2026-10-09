package org.danteplanner.backend.integration.consistency;

import org.danteplanner.backend.integration.consistency.Plan.FaultWindow;

final class FaultScheduler {

    private final Replication replication;
    private final FaultWindow window;

    FaultScheduler(Replication replication, FaultWindow window) {
        this.replication = replication;
        this.window = window;
    }

    void reach(int operationIndex) {
        if (operationIndex == window.stopAt()) {
            replication.stopReplica();
        }
        if (operationIndex == window.startAt()) {
            replication.startReplica();
        }
    }
}

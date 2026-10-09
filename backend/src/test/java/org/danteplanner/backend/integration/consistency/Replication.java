package org.danteplanner.backend.integration.consistency;

public interface Replication {

    void stopReplica();

    void startReplica();

    void awaitCaughtUp();
}

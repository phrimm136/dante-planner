package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RecordingReplication implements Replication {

    final List<String> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void stopReplica() {
        events.add("stop");
    }

    @Override
    public void startReplica() {
        events.add("start");
    }

    @Override
    public void awaitCaughtUp() {
        events.add("await");
    }

    @Override
    public void applyLatency(long millis) {
        events.add("latency " + millis);
    }

    @Override
    public void removeLatency() {
        events.add("remove latency");
    }
}

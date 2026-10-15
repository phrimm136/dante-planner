package org.danteplanner.backend.shared.gtid;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.freshness.MarkerStore;

final class RecordingMarkerStore extends MarkerStore {

    record Raise(Set<FreshnessScope> scopes, String gtidSet) {
    }

    final List<Raise> raises = new ArrayList<>();

    RecordingMarkerStore() {
        super(null, null, List.of(), new Settings(0, Duration.ofSeconds(1), Duration.ofHours(1)), new SimpleMeterRegistry());
    }

    @Override
    public void raise(Collection<FreshnessScope> scopes, String gtidSet) {
        raises.add(new Raise(Set.copyOf(scopes), gtidSet));
    }
}

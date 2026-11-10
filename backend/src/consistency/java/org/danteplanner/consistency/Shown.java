package org.danteplanner.consistency;

import java.util.Map;
import java.util.Set;

public record Shown(Map<String, Long> revisions, Set<String> masked) implements ThreadResult {

    public Shown {
        revisions = Map.copyOf(revisions);
        masked = Set.copyOf(masked);
    }
}

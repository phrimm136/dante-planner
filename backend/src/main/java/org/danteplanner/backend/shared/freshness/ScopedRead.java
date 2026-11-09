package org.danteplanner.backend.shared.freshness;

import java.util.Objects;
import java.util.Set;

public record ScopedRead<T>(T value, Set<FreshnessScope> scopes) {

    public ScopedRead {
        Objects.requireNonNull(value, "value");
        scopes = Set.copyOf(scopes);
    }
}

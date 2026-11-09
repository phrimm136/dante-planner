package org.danteplanner.backend.shared.freshness;

import java.util.Set;

import org.danteplanner.backend.shared.exception.EntityNotFoundException;

public abstract class ScopedMiss extends EntityNotFoundException {

    private final Set<FreshnessScope> scopes;

    protected ScopedMiss(String errorCode, String message, Set<FreshnessScope> scopes) {
        super(errorCode, message);
        this.scopes = Set.copyOf(scopes);
    }

    public Set<FreshnessScope> scopes() {
        return scopes;
    }
}

package org.danteplanner.backend.auth.token;

/**
 * <p>Persisted entries written as {@code USED} before the rename to {@code RETIRED}
 * may survive in Redis for up to one family TTL; readers map them to {@link #RETIRED}.</p>
 */
public enum RotationState {
    UNUSED_LATEST,
    PENDING,
    RETIRED,
    SUPERSEDED;

    private static final String LEGACY_RETIRED_NAME = "USED";

    static RotationState of(String name) {
        return LEGACY_RETIRED_NAME.equals(name) ? RETIRED : valueOf(name);
    }
}

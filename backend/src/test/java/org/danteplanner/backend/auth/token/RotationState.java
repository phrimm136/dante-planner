package org.danteplanner.backend.auth.token;

public enum RotationState {
    UNUSED_LATEST,
    PENDING,
    RETIRED,
    SUPERSEDED
}

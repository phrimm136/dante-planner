package org.danteplanner.backend.auth.token;

/**
 * Lifecycle state of a refresh token within its rotation lineage.
 *
 * <p>State machine: a freshly minted token is {@link #UNUSED_LATEST}; on rotation it
 * moves to {@link #PENDING} until its successor is first used ({@link #RETIRED}) or a
 * retry outside the reuse window mints a replacement successor ({@link #SUPERSEDED}).
 * Presenting a {@link #RETIRED} or {@link #SUPERSEDED} token signals theft and revokes
 * the whole family.</p>
 *
 * <p>Persisted entries written as {@code USED} before the rename to {@code RETIRED}
 * may survive in Redis for up to one family TTL; readers map them to {@link #RETIRED}.</p>
 */
public enum RotationState {
    UNUSED_LATEST,
    PENDING,
    RETIRED,
    SUPERSEDED
}

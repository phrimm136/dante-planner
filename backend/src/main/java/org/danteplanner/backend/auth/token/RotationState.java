package org.danteplanner.backend.auth.token;

/**
 * Lifecycle state of a refresh token within its rotation lineage.
 *
 * <p>State machine: a freshly minted token is {@link #UNUSED_LATEST}; on rotation it
 * moves to {@link #PENDING} until its successor is first used ({@link #USED}) or a retry
 * mints a replacement successor ({@link #SUPERSEDED}). Presenting a {@link #USED} or
 * {@link #SUPERSEDED} token signals theft and revokes the whole family.</p>
 */
public enum RotationState {
    UNUSED_LATEST,
    PENDING,
    USED,
    SUPERSEDED
}

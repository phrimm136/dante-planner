package org.danteplanner.backend.shared.failure;

/**
 * The transaction proxy keys rollback off an unchecked throw, so a failure handed back as a value
 * commits whatever the method wrote before it decided to fail.
 */
public interface FailureUnion {
}

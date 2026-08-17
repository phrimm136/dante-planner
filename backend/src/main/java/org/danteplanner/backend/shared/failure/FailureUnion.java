package org.danteplanner.backend.shared.failure;

/**
 * Marks a sealed union whose variants a JVM-internal caller branches on, at least one of them a
 * failure.
 *
 * <p>A marked union may not cross the transaction proxy. The proxy keys rollback off an unchecked
 * throw, so a failure handed back as a value commits whatever the method wrote before it decided to
 * fail; a failure that must undo writes travels as a throw instead. {@code
 * FailureUnionBoundaryTest} fails the build when a {@code @Transactional} method declares a marked
 * type as its return type.</p>
 */
public interface FailureUnion {
}

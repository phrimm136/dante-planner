package org.danteplanner.backend.shared.exception;

/**
 * The kind of failure a {@link DomainException} reports, named in transport-neutral terms.
 *
 * <p>A domain exception states what went wrong; which HTTP status carries that outcome is the web
 * layer's decision, so the mapping lives in {@code GlobalExceptionHandler} and not here.</p>
 */
public enum ErrorKind {

    /** The addressed thing does not exist, or the caller may not learn that it does. */
    NOT_FOUND,

    /** The thing exists and the caller may not act on it. */
    FORBIDDEN,

    /** The action collides with the resource's current state. */
    CONFLICT,

    /** The caller supplied something the endpoint cannot accept. */
    INVALID_REQUEST
}

package org.danteplanner.backend.shared.exception;

/**
 * The kind of failure a {@link DomainException} reports, named in transport-neutral terms.
 */
public enum ErrorKind {

    /** The addressed thing does not exist, or the caller may not learn that it does. */
    NOT_FOUND,

    /** The thing exists and the caller may not act on it. */
    FORBIDDEN,

    /** The action collides with the resource's current state. */
    CONFLICT,

    /** The caller supplied something the endpoint cannot accept. */
    INVALID_REQUEST,

    /** The caller has not established who it is, or no longer holds a valid credential. */
    UNAUTHENTICATED,

    /** The caller has spent an allowance and must wait before asking again. */
    OVER_QUOTA
}

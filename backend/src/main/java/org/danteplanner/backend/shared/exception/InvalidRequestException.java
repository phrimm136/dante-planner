package org.danteplanner.backend.shared.exception;

/**
 * A client supplied a value the endpoint cannot accept, mapped to HTTP 400 by
 * {@link GlobalExceptionHandler}.
 *
 * <p>Lives in {@code shared} for the rejections no feature exception already covers. A feature
 * that owns a richer error — planner content, moderation authority, vote immutability — throws its
 * own type instead.</p>
 *
 * <p>The message reaches the client, so it may name only what the caller sent. An invariant the
 * caller cannot influence is a bug, not a rejection, and belongs in an
 * {@link IllegalArgumentException} that the handler reports as a server error.</p>
 */
public class InvalidRequestException extends DomainException {

    public InvalidRequestException(String errorCode, String message) {
        super(ErrorKind.INVALID_REQUEST, errorCode, message);
    }
}

package org.danteplanner.backend.shared.exception;

/**
 * The message reaches the client, so it may name only what the caller sent.
 */
public class InvalidRequestException extends DomainException {

    public InvalidRequestException(String errorCode, String message) {
        super(ErrorKind.INVALID_REQUEST, errorCode, message);
    }
}

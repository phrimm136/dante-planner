package org.danteplanner.backend.shared.exception;

import lombok.Getter;

/**
 * A business error whose whole response is its kind, its code, and its message.
 *
 * <p>{@code GlobalExceptionHandler} answers every subclass through one handler, so a new business
 * error is a new class here and no edit there. An error needing more of the response than the
 * triple below — a cleared cookie, an extra body field, a message that must hide what the
 * exception carries — extends {@link RuntimeException} and keeps its own handler instead.</p>
 */
@Getter
public abstract class DomainException extends RuntimeException {

    private final ErrorKind kind;
    private final String errorCode;

    protected DomainException(ErrorKind kind, String errorCode, String message) {
        super(message);
        this.kind = kind;
        this.errorCode = errorCode;
    }
}

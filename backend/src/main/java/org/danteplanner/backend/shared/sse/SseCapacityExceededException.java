package org.danteplanner.backend.shared.sse;

import lombok.Getter;
import org.danteplanner.backend.shared.exception.Problems;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.util.UUID;

/**
 * Raised when a planner's SSE registry is full and the arriving subscriber cannot be admitted.
 *
 * <p>Extends {@link ErrorResponseException} rather than {@code DomainException} because the
 * subscription service raises it directly, and a {@code DomainException} constructed inside a
 * {@code ..service..} class is a rule stated outside a rule component.</p>
 */
@Getter
public class SseCapacityExceededException extends ErrorResponseException {

    private static final String ERROR_CODE = "SSE_CAPACITY_EXCEEDED";
    private static final String CLIENT_DETAIL = "Too many active connections for this planner";

    private final UUID plannerId;

    public SseCapacityExceededException(UUID plannerId, int maxConnections) {
        super(HttpStatus.TOO_MANY_REQUESTS,
                Problems.problem(HttpStatus.TOO_MANY_REQUESTS, ERROR_CODE, CLIENT_DETAIL), null);
        getHeaders().set(HttpHeaders.RETRY_AFTER,
                String.valueOf(SseConstants.CAPACITY_RETRY_AFTER_SECONDS));
        this.plannerId = plannerId;
    }
}

package org.danteplanner.backend.shared.sse;

import lombok.Getter;
import org.danteplanner.backend.shared.exception.Problems;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.util.UUID;

@Getter
public class SseCapacityExceededException extends ErrorResponseException {

    private static final String ERROR_CODE = "SSE_CAPACITY_EXCEEDED";
    private static final String CLIENT_DETAIL = "Too many active connections for this planner";

    private final UUID plannerId;
    private final int maxConnections;

    public SseCapacityExceededException(UUID plannerId, int maxConnections) {
        super(HttpStatus.TOO_MANY_REQUESTS);
        Problems.fill(getBody(), ERROR_CODE, CLIENT_DETAIL);
        getHeaders().set(HttpHeaders.RETRY_AFTER,
                String.valueOf(SseConstants.CAPACITY_RETRY_AFTER_SECONDS));
        this.plannerId = plannerId;
        this.maxConnections = maxConnections;
    }

    @Override
    public String getMessage() {
        return "SSE connection limit reached for planner " + plannerId + " (max " + maxConnections + ")";
    }
}

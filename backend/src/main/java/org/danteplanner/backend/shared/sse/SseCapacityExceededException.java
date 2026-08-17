package org.danteplanner.backend.shared.sse;

import java.util.UUID;

import lombok.Getter;

/**
 * Raised when a planner's SSE registry is full and the arriving subscriber cannot be admitted.
 */
@Getter
public class SseCapacityExceededException extends RuntimeException {

    private final UUID plannerId;

    public SseCapacityExceededException(UUID plannerId, int maxConnections) {
        super("SSE connection limit reached for planner " + plannerId + " (max " + maxConnections + ")");
        this.plannerId = plannerId;
    }
}

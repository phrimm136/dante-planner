package org.danteplanner.backend.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class PlannerForbiddenException extends RuntimeException {

    private final UUID plannerId;

    public PlannerForbiddenException(UUID plannerId) {
        super("User is not authorized to modify planner with id: " + plannerId);
        this.plannerId = plannerId;
    }
}

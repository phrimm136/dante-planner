package org.danteplanner.backend.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class PlannerNotFoundException extends RuntimeException {

    private final UUID plannerId;

    public PlannerNotFoundException(UUID plannerId) {
        super("Planner not found with id: " + plannerId);
        this.plannerId = plannerId;
    }
}

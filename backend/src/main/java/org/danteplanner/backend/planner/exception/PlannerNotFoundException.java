package org.danteplanner.backend.planner.exception;

import java.util.UUID;

import lombok.Getter;

import org.danteplanner.backend.shared.exception.EntityNotFoundException;

@Getter
public class PlannerNotFoundException extends EntityNotFoundException {

    private final UUID plannerId;

    public PlannerNotFoundException(UUID plannerId) {
        super("Planner not found with id: " + plannerId);
        this.plannerId = plannerId;
    }
}

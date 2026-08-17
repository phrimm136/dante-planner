package org.danteplanner.backend.planner.exception;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

import lombok.Getter;

import java.util.UUID;

@Getter
public class PlannerForbiddenException extends DomainException {

    private final UUID plannerId;

    public PlannerForbiddenException(UUID plannerId) {
        super(ErrorKind.FORBIDDEN, "PLANNER_FORBIDDEN", "User is not authorized to modify planner with id: " + plannerId);
        this.plannerId = plannerId;
    }
}

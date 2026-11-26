package org.danteplanner.backend.planner.exception;

import lombok.Getter;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

import java.util.UUID;

@Getter
public class PlannerConcurrentWriteException extends DomainException {

    private final UUID plannerId;

    public PlannerConcurrentWriteException(UUID plannerId) {
        super(ErrorKind.CONFLICT, "CONCURRENT_WRITE", "The resource was modified concurrently");
        this.plannerId = plannerId;
    }
}

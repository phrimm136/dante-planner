package org.danteplanner.fixture.guaranteeplacement.service;

import java.util.UUID;

import org.danteplanner.backend.planner.exception.PlannerConcurrentWriteException;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;

public class RefusingService {

    void refuse(UUID plannerId) {
        throw new PlannerForbiddenException(plannerId);
    }

    void trip(UUID plannerId) {
        throw new PlannerConcurrentWriteException(plannerId);
    }
}

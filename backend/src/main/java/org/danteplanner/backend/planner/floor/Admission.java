package org.danteplanner.backend.planner.floor;

import org.danteplanner.backend.shared.failure.FailureUnion;

import java.util.List;

public sealed interface Admission extends FailureUnion permits Admission.Admitted, Admission.Rejected {

    record Admitted(List<FloorSelection> floors, List<Violation> boundaryViolations) implements Admission {

        public Admitted {
            floors = List.copyOf(floors);
            boundaryViolations = List.copyOf(boundaryViolations);
        }
    }

    record Rejected(List<Violation> violations) implements Admission {

        public Rejected {
            violations = List.copyOf(violations);
        }
    }
}

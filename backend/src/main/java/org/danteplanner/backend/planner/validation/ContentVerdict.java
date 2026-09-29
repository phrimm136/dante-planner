package org.danteplanner.backend.planner.validation;

import java.util.List;

public sealed interface ContentVerdict permits ContentVerdict.Accepted, ContentVerdict.Refused, ContentVerdict.Rejected {

    record Accepted(PlannerDocument document) implements ContentVerdict {
    }

    record Refused(Violation violation) implements ContentVerdict {
    }

    record Rejected(List<Violation> violations) implements ContentVerdict {

        public Rejected {
            violations = List.copyOf(violations);
        }
    }
}

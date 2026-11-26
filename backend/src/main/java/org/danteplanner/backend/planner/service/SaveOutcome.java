package org.danteplanner.backend.planner.service;

import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.shared.failure.CommandOutcome;

public sealed interface SaveOutcome extends CommandOutcome {

    StoredPlanner planner();

    record Created(StoredPlanner planner) implements SaveOutcome {
    }

    record Updated(StoredPlanner planner) implements SaveOutcome {
    }

    record Acknowledged(StoredPlanner planner) implements SaveOutcome {
    }
}

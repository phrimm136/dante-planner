package org.danteplanner.backend.planner.entity;

import org.danteplanner.backend.shared.failure.CommandOutcome;

public enum PublicationOutcome implements CommandOutcome {

    NONE,

    FIRST_PUBLISH,

    REPUBLISH,

    WITHDRAWN;

    public boolean changed() {
        return this != NONE;
    }
}

package org.danteplanner.backend.planner.entity;

import org.danteplanner.backend.shared.failure.CommandOutcome;
import org.danteplanner.backend.shared.failure.WriteOutcome;

import java.time.Instant;

public enum PublishOutcome implements CommandOutcome {

    NONE,

    FIRST_PUBLISH,

    REPUBLISH;

    public PublishOutcome when(WriteOutcome write) {
        return switch (write) {
            case APPLIED -> this;
            case NOTHING -> NONE;
        };
    }

    public Publication after(Publication before, Instant now) {
        return switch (this) {
            case NONE -> before;
            case FIRST_PUBLISH, REPUBLISH -> before.published(now);
        };
    }
}

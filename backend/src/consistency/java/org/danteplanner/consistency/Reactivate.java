package org.danteplanner.consistency;

public record Reactivate(String session, long invokedAt, long completedAt, VisibilityResult result)
        implements Operation {
}

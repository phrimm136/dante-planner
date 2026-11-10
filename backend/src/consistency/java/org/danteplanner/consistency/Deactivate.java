package org.danteplanner.consistency;

public record Deactivate(String session, long invokedAt, long completedAt, VisibilityResult result)
        implements Operation {
}

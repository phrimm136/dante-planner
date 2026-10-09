package org.danteplanner.consistency;

public record Publish(String session, String key, long invokedAt, long completedAt, VisibilityResult result)
        implements Operation {
}

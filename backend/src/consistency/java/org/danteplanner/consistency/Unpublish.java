package org.danteplanner.consistency;

public record Unpublish(String session, String key, long invokedAt, long completedAt, VisibilityResult result)
        implements Operation {
}

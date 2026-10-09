package org.danteplanner.consistency;

public record Save(String session, String key, long invokedAt, long completedAt, SaveResult result)
        implements Operation {
}

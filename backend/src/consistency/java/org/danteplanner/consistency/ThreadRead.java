package org.danteplanner.consistency;

public record ThreadRead(String session, String key, long invokedAt, long completedAt, ThreadResult result)
        implements Operation {
}

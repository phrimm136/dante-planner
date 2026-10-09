package org.danteplanner.consistency;

public record Read(String session, String key, long invokedAt, long completedAt, ReadResult result)
        implements Operation {
}

package org.danteplanner.consistency;

public record PublicRead(String session, String key, long invokedAt, long completedAt, ReadResult result)
        implements Operation {
}

package org.danteplanner.consistency;

public record Delete(String session, String key, long invokedAt, long completedAt, DeleteResult result)
        implements Operation {
}

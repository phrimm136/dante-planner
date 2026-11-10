package org.danteplanner.consistency;

public record ThreadRead(String session, String key, Audience audience, long invokedAt, long completedAt,
        ThreadResult result) implements Operation {

    public enum Audience {
        OWNER,
        PUBLIC
    }
}

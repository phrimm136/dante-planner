package org.danteplanner.consistency;

public record CommentWrite(String session, String key, String comment, long revision, long invokedAt,
        long completedAt, VisibilityResult result) implements Operation {
}

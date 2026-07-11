package org.danteplanner.backend.shared.sse;

/**
 * Redis pub/sub channel names for cross-node SSE fan-out.
 */
public final class SseChannels {

    public static final String USER = "sse:user";
    public static final String COMMENT = "sse:comment";

    private SseChannels() {
    }
}

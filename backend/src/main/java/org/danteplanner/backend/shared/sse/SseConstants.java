package org.danteplanner.backend.shared.sse;

/**
 * Environment-invariant constants for the SSE connection layer.
 */
public final class SseConstants {

    /**
     * Threads the heartbeat sweep fans out onto, and therefore the number of stalled peers the
     * layer absorbs before a heartbeat send has to wait for a free worker.
     */
    public static final int HEARTBEAT_WORKER_POOL_SIZE = 8;

    /** Sweep period of the per-user stream. Offset from the comment stream's to spread the load. */
    public static final long USER_STREAM_HEARTBEAT_INTERVAL_MS = 10_000L;

    /** Sweep period of the per-planner comment stream. */
    public static final long COMMENT_STREAM_HEARTBEAT_INTERVAL_MS = 15_000L;

    /**
     * Seconds a subscriber refused for capacity is told to wait. Matches the frontend's slowest
     * reconnect wait — the 8s backoff ceiling plus its 5s jitter ceiling — so a client honouring
     * the header never returns sooner than its own backoff would have sent it.
     */
    public static final int CAPACITY_RETRY_AFTER_SECONDS = 13;

    private SseConstants() {
    }
}

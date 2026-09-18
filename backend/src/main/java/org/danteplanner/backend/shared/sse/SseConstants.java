package org.danteplanner.backend.shared.sse;

public final class SseConstants {

    public static final int HEARTBEAT_WORKER_POOL_SIZE = 8;

    public static final long USER_STREAM_HEARTBEAT_INTERVAL_MS = 10_000L;

    public static final long COMMENT_STREAM_HEARTBEAT_INTERVAL_MS = 15_000L;

    public static final int CAPACITY_RETRY_AFTER_SECONDS = 13;

    public static final int PUBLISH_MAX_ATTEMPTS = 3;

    public static final long PUBLISH_RETRY_DELAY_MS = 50L;

    public static final double PUBLISH_RETRY_MULTIPLIER = 2.0;

    private SseConstants() {
    }
}

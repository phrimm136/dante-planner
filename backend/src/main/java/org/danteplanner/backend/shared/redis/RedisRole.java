package org.danteplanner.backend.shared.redis;

import org.danteplanner.backend.shared.config.RedisConnectionConfig;

public enum RedisRole {

    AUTH("auth"),
    AUTH_LOCAL("auth-local"),
    RATE_LIMIT("rate-limit"),
    SSE_LOCAL("sse-local");

    private static final String BREAKER_NAME_PREFIX = "redis-";

    private final String label;

    RedisRole(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String breakerName() {
        return BREAKER_NAME_PREFIX + label;
    }

    public RedisConnectionConfig.Endpoints.Endpoint endpointIn(RedisConnectionConfig connections) {
        return switch (this) {
            case AUTH -> connections.endpoints().auth();
            case AUTH_LOCAL -> connections.endpoints().authLocal();
            case RATE_LIMIT -> connections.endpoints().rateLimit();
            case SSE_LOCAL -> connections.endpoints().sseLocal();
        };
    }
}

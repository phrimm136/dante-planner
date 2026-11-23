package org.danteplanner.backend.support;

import org.danteplanner.backend.shared.config.RedisConnectionConfig.Endpoints;

import org.danteplanner.backend.shared.config.RedisConnectionConfig.Endpoints.Endpoint;

import org.danteplanner.backend.shared.config.RedisConnectionConfig;
import org.danteplanner.backend.shared.redis.RedisRole;

public final class RedisEndpoints {

    private RedisEndpoints() {
    }

    public static RedisConnectionConfig everyRoleAt(String host, int port) {
        Endpoint endpoint = Endpoint.at(host, port);
        return new RedisConnectionConfig(new Endpoints(endpoint, endpoint, endpoint, endpoint));
    }

    public static RedisConnectionConfig withRoleAt(
            RedisConnectionConfig connections, RedisRole moved, String host, int port) {
        return new RedisConnectionConfig(new Endpoints(
                endpoint(connections, RedisRole.AUTH, moved, host, port),
                endpoint(connections, RedisRole.RATE_LIMIT, moved, host, port),
                endpoint(connections, RedisRole.SSE_LOCAL, moved, host, port),
                endpoint(connections, RedisRole.AUTH_LOCAL, moved, host, port)));
    }

    private static Endpoint endpoint(
            RedisConnectionConfig connections, RedisRole role, RedisRole moved, String host, int port) {
        return role == moved ? Endpoint.at(host, port) : role.endpointIn(connections);
    }
}

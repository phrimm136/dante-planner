package org.danteplanner.backend.support;

import org.danteplanner.backend.shared.config.RedisConnectionConfig;
import org.danteplanner.backend.shared.redis.RedisRole;

public final class RedisEndpoints {

    private RedisEndpoints() {
    }

    public static RedisConnectionConfig everyRoleAt(String host, int port) {
        RedisConnectionConfig connections = new RedisConnectionConfig();
        for (RedisRole role : RedisRole.values()) {
            moveRole(connections, role, host, port);
        }
        return connections;
    }

    public static void moveRole(RedisConnectionConfig connections, RedisRole role, String host, int port) {
        RedisConnectionConfig.Endpoint endpoint = role.endpointIn(connections);
        endpoint.setHost(host);
        endpoint.setPort(port);
    }
}

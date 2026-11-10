package org.danteplanner.backend.shared.redis;

import java.util.concurrent.CompletableFuture;

public interface RoleConnector {

    RedisRole role();

    CompletableFuture<Void> connect();
}

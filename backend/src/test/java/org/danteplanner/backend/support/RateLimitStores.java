package org.danteplanner.backend.support;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;

import org.danteplanner.backend.shared.ratelimit.RateLimitStore;

public final class RateLimitStores {

    private static final ClientResources CLIENT_RESOURCES = DefaultClientResources.create();

    private RateLimitStores() {
    }

    public static RateLimitStore connectedTo(String host, int port, Duration bucketTtl) {
        RateLimitStore store = new RateLimitStore(CLIENT_RESOURCES, host, port, bucketTtl);
        try {
            Hang.get(store.connect(), "the rate-limit store connecting to " + host + ":" + port);
        } catch (ExecutionException e) {
            store.close();
            throw new IllegalStateException("the rate-limit store did not connect", e.getCause());
        }
        return store;
    }
}

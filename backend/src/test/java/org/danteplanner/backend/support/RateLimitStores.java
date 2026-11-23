package org.danteplanner.backend.support;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;

import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.shared.ratelimit.RateLimitProperties;
import org.danteplanner.backend.shared.ratelimit.RateLimitProperties.BucketConfig;
import org.danteplanner.backend.shared.ratelimit.RateLimitStore;

public final class RateLimitStores {

    private static final ClientResources CLIENT_RESOURCES = DefaultClientResources.create();

    private RateLimitStores() {
    }

    public static RateLimitProperties limits(long configVersion, Map<RateLimitPolicy, BucketConfig> limits) {
        return new RateLimitProperties(configVersion,
                limit(limits, RateLimitPolicy.CRUD),
                limit(limits, RateLimitPolicy.SSE),
                limit(limits, RateLimitPolicy.AUTH),
                limit(limits, RateLimitPolicy.COMMENT),
                limit(limits, RateLimitPolicy.REPORT),
                limit(limits, RateLimitPolicy.MODERATION),
                limit(limits, RateLimitPolicy.PUBLIC_READ),
                limit(limits, RateLimitPolicy.PLANNER_STATS),
                limit(limits, RateLimitPolicy.VIEW_RECORD));
    }

    private static BucketConfig limit(Map<RateLimitPolicy, BucketConfig> limits, RateLimitPolicy policy) {
        return limits.getOrDefault(policy, new BucketConfig(0, 0, 0));
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

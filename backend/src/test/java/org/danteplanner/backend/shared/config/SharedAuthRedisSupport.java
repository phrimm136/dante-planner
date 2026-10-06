package org.danteplanner.backend.shared.config;

import org.danteplanner.backend.integration.SharedRedisContainerSupport;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

abstract class SharedAuthRedisSupport {

    @DynamicPropertySource
    static void authRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("AUTH_REDIS_HOST", SharedRedisContainerSupport::host);
        registry.add("AUTH_REDIS_PORT", SharedRedisContainerSupport::port);
    }
}

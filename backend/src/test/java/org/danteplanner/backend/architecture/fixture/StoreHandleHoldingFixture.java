package org.danteplanner.backend.architecture.fixture;

import org.springframework.data.redis.core.StringRedisTemplate;

public class StoreHandleHoldingFixture {

    private final StringRedisTemplate template;

    public StoreHandleHoldingFixture(StringRedisTemplate template) {
        this.template = template;
    }

    public boolean holdsTemplate() {
        return template != null;
    }
}

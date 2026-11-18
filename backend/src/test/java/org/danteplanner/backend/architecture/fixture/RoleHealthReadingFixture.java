package org.danteplanner.backend.architecture.fixture;

import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;

public class RoleHealthReadingFixture {

    public boolean isAuthUp(RedisRoleHealth health) {
        return health.isAvailable(RedisRole.AUTH);
    }
}

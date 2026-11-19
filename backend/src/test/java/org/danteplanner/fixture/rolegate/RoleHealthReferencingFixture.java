package org.danteplanner.fixture.rolegate;

import java.util.function.Predicate;

import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;

public class RoleHealthReferencingFixture {

    public Predicate<RedisRole> availability(RedisRoleHealth health) {
        return health::isAvailable;
    }
}

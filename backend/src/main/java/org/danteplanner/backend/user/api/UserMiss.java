package org.danteplanner.backend.user.api;

import java.util.Set;

import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.freshness.ScopedMiss;

public class UserMiss extends ScopedMiss {

    public UserMiss(long userId) {
        super("USER_NOT_FOUND", "User not found with id: " + userId, Set.of(new FreshnessScope.User(userId)));
    }
}

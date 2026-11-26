package org.danteplanner.backend.auth.token;

import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.Users;

import java.util.Date;

public record AccessClaims(Long userId, UserRole role, Date issuedAt, Date expiration) {

    public boolean isSentinel() {
        return userId.equals(Users.SENTINEL_USER_ID);
    }
}

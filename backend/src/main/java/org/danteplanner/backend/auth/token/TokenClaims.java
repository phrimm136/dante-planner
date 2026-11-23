package org.danteplanner.backend.auth.token;

import org.springframework.util.Assert;

import org.danteplanner.backend.user.api.UserRole;

import java.util.Date;

public record TokenClaims(
        Long userId,
        String type,
        UserRole role,
        Date issuedAt,
        Date expiration,
        RefreshLineage lineage
) {
    public TokenClaims {
        Assert.notNull(userId, "userId must not be null");
        Assert.hasText(type, "type must not be null or blank");
        Assert.notNull(issuedAt, "issuedAt must not be null");
        Assert.notNull(expiration, "expiration must not be null");
        Assert.isTrue(!expiration.before(issuedAt), "expiration must be after issuedAt");
        Assert.notNull(lineage, "lineage must not be null");
    }

    public static final String TYPE_ACCESS = "access";

    public static final String TYPE_REFRESH = "refresh";

    public boolean isAccessToken() {
        return TYPE_ACCESS.equals(type);
    }

    public boolean isRefreshToken() {
        return TYPE_REFRESH.equals(type);
    }

    public boolean isExpired() {
        return expiration.before(new Date());
    }
}

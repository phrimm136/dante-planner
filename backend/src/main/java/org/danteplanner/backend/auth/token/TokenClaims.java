package org.danteplanner.backend.auth.token;

import org.springframework.util.Assert;

import org.danteplanner.backend.user.entity.UserRole;

import java.util.Date;

public record TokenClaims(
        Long userId,
        String type,
        UserRole role,
        Date issuedAt,
        Date expiration,
        String jti,
        String familyId,
        String parentJti
) {
    public TokenClaims {
        Assert.notNull(userId, "userId must not be null");
        Assert.hasText(type, "type must not be null or blank");
        Assert.notNull(issuedAt, "issuedAt must not be null");
        Assert.notNull(expiration, "expiration must not be null");
        Assert.isTrue(!expiration.before(issuedAt), "expiration must be after issuedAt");
    }

    public TokenClaims(Long userId, String type, UserRole role, Date issuedAt, Date expiration) {
        this(userId, type, role, issuedAt, expiration, null, null, null);
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

    public UserRole getEffectiveRole() {
        return role != null ? role : UserRole.NORMAL;
    }
}

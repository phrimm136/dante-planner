# Refresh Token Rotation

## The Problem
If a refresh token is stolen, attacker can generate new access tokens indefinitely.

## Solution 1: Simple Refresh (No Rotation)
```
Refresh token lasts 30 days
Can be used multiple times
If stolen: Attacker has 30 days of access
```

**Risk:** No detection of token theft

## Solution 2: Refresh Token Rotation
```
Use refresh token → Get new access + refresh tokens
Old refresh token is blacklisted
Can only use each refresh token ONCE
```

**How it detects theft:**
```
Day 1: User gets tokens (A, R1)
Day 2: Attacker steals R1
Day 3: Attacker uses R1 → Gets (A2, R2), R1 blacklisted
Day 4: User tries to use R1 → REJECTED (blacklisted)
Day 4: System knows token was stolen → Invalidates everything
```

## Implementation in LimbusPlanner

```java
// JwtAuthenticationFilter.java:266
private boolean attemptAutoRefresh(...) {
    String refreshToken = getCookie(REFRESH_TOKEN);

    // Validate token
    TokenClaims claims = tokenValidator.validateToken(refreshToken);

    // Check if already used (blacklisted)
    if (tokenBlacklistService.isBlacklisted(refreshToken)) {
        log.warn("Attempted reuse of blacklisted token");
        return false; // THEFT DETECTED
    }

    // Blacklist old token (can only use once)
    tokenBlacklistService.blacklistToken(refreshToken, claims.expiration());

    // Generate new pair
    String newAccessToken = tokenGenerator.generateAccessToken(user);
    String newRefreshToken = tokenGenerator.generateRefreshToken(user);

    return true;
}
```

## Trade-offs

**Benefits:**
- Detects token theft
- Limits damage from stolen tokens
- Forced re-login on detection

**Costs:**
- Need blacklist storage
- Need cleanup job
- Race conditions if user has multiple devices

## When NOT to Use

- Single device only (no theft scenario)
- Offline applications (can't check blacklist)
- Extremely high traffic (blacklist overhead)

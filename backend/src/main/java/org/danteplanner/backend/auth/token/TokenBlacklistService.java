package org.danteplanner.backend.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import org.springframework.data.redis.core.StringRedisTemplate;

import org.danteplanner.backend.shared.redis.RedisKeyScanner;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis-backed token blacklist service with TTL support.
 * Per-token revocations live in the auth Redis so logout and rotation
 * are visible across all pods in a multi-server deployment.
 *
 * <p>Tokens are stored as SHA-256 hashes to avoid keeping
 * sensitive token values in Redis. Entries auto-expire via Redis TTL
 * set from the token's original expiration time.</p>
 *
 * <p>Also supports user-level token invalidation via timestamp.
 * When a user is demoted, all their tokens issued before the
 * invalidation time are rejected.</p>
 */
@Service
@Slf4j
public class TokenBlacklistService {

    /**
     * Grace period for rotation-blacklisted tokens (milliseconds).
     * Concurrent requests carrying the same refresh token are allowed
     * within this window during token rotation. Logout-blacklisted
     * tokens bypass this grace period entirely.
     */
    private static final long ROTATION_GRACE_PERIOD_MS = 5_000;

    private static final String BLACKLIST_KEY_PREFIX = "bl:";

    private static final String USER_INVALIDATION_KEY_PREFIX = "uinv:";

    private static final long DEFAULT_REFRESH_TOKEN_EXPIRY_MS = 604800000;

    private static final String BLACKLIST_CHECK_SKIPPED_COUNTER = "blacklist_check_skipped_total";

    /**
     * Refresh token expiry in milliseconds (injected from config).
     * Used to calculate TTL for user invalidation entries.
     */
    @Value("${jwt.refresh-token-expiry:604800000}")
    private long refreshTokenExpiry = DEFAULT_REFRESH_TOKEN_EXPIRY_MS;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Auth-local read replica template. Blacklist and user-invalidation READS are
     * served from here (read-local); WRITES stay on {@link #stringRedisTemplate}.
     */
    private final StringRedisTemplate authLocalStringRedisTemplate;

    private final MeterRegistry meterRegistry;

    public TokenBlacklistService(
            StringRedisTemplate stringRedisTemplate,
            @Qualifier("authLocalStringRedisTemplate") StringRedisTemplate authLocalStringRedisTemplate,
            MeterRegistry meterRegistry) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.authLocalStringRedisTemplate = authLocalStringRedisTemplate;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Adds a token to the blacklist with immediate effect.
     * Used for logout — no grace period, token is rejected instantly.
     *
     * @param token the token to blacklist
     * @param expiry the token's expiration date (entries auto-expire after this)
     */
    public void blacklistToken(String token, Date expiry) {
        addToBlacklist(token, expiry, true);
    }

    /**
     * Adds a token to the blacklist with a grace period.
     * Used during refresh token rotation — concurrent requests carrying
     * the same token are allowed within {@link #ROTATION_GRACE_PERIOD_MS}.
     *
     * @param token the token to blacklist
     * @param expiry the token's expiration date (entries auto-expire after this)
     */
    public void blacklistTokenForRotation(String token, Date expiry) {
        addToBlacklist(token, expiry, false);
    }

    private void addToBlacklist(String token, Date expiry, boolean immediate) {
        if (token == null || expiry == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long remaining = expiry.getTime() - now;
        if (remaining <= 0) {
            return;
        }

        String key = blacklistKey(token);
        stringRedisTemplate.opsForValue().set(key, encode(immediate, now), Duration.ofMillis(remaining));
    }

    /**
     * Checks if a token is blacklisted.
     * Rotation-blacklisted tokens are allowed within the grace period.
     *
     * @param token the token to check
     * @return true if the token is blacklisted and outside any grace period
     */
    public boolean isBlacklisted(String token) {
        if (token == null) {
            return false;
        }

        try {
            String value = authLocalStringRedisTemplate.opsForValue().get(blacklistKey(token));
            if (value == null) {
                return false;
            }

            long now = System.currentTimeMillis();
            boolean immediate = decodeImmediate(value);
            long blacklistedAt = decodeBlacklistedAt(value);

            if (!immediate && now - blacklistedAt < ROTATION_GRACE_PERIOD_MS) {
                return false;
            }

            return true;
        } catch (DataAccessException e) {
            return failOpen("Blacklist check failed open due to Redis error", e);
        }
    }

    /**
     * Invalidates all tokens for a specific user.
     * Any token issued before this call will be rejected.
     * Used when demoting users to immediately revoke their access.
     *
     * @param userId the user whose tokens should be invalidated
     */
    public void invalidateUserTokens(Long userId) {
        if (userId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        stringRedisTemplate.opsForValue().set(
                userInvalidationKey(userId), String.valueOf(now), Duration.ofMillis(refreshTokenExpiry));
        log.info("Invalidated all tokens for user {}", userId);
    }

    /**
     * Checks if a token was issued before the user's tokens were invalidated.
     *
     * @param userId the user ID
     * @param issuedAt when the token was issued (milliseconds)
     * @return true if the token was issued before invalidation
     */
    public boolean isUserTokenInvalidated(Long userId, long issuedAt) {
        if (userId == null) {
            return false;
        }
        try {
            String value = authLocalStringRedisTemplate.opsForValue().get(userInvalidationKey(userId));
            if (value == null) {
                return false;
            }
            return issuedAt < Long.parseLong(value);
        } catch (DataAccessException e) {
            return failOpen("User invalidation check failed open due to Redis error", e);
        }
    }

    /**
     * Clears user invalidation entry (for testing or when user re-authenticates).
     *
     * @param userId the user whose invalidation should be cleared
     */
    public void clearUserInvalidation(Long userId) {
        if (userId != null) {
            stringRedisTemplate.delete(userInvalidationKey(userId));
        }
    }

    /**
     * Returns the current size of the blacklist.
     * Useful for monitoring and testing.
     */
    public int size() {
        return RedisKeyScanner.scanKeys(stringRedisTemplate, BLACKLIST_KEY_PREFIX + "*").size();
    }

    /**
     * Returns the number of users with invalidated tokens.
     * Useful for monitoring and testing.
     */
    public int userInvalidationSize() {
        return RedisKeyScanner.scanKeys(stringRedisTemplate, USER_INVALIDATION_KEY_PREFIX + "*").size();
    }

    /**
     * Clears all entries from the blacklist and user invalidations.
     * Primarily for testing purposes.
     */
    public void clear() {
        Set<String> keys = RedisKeyScanner.scanKeys(stringRedisTemplate, BLACKLIST_KEY_PREFIX + "*");
        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        Set<String> userKeys = RedisKeyScanner.scanKeys(stringRedisTemplate, USER_INVALIDATION_KEY_PREFIX + "*");
        if (!userKeys.isEmpty()) {
            stringRedisTemplate.delete(userKeys);
        }
    }

    /**
     * Fails open when a Redis read errors: logs the reason, increments the
     * skip counter, and treats the token as valid so auth stays available.
     *
     * @param message the warning to log
     * @param e       the originating Redis access error
     * @return false — the token is not treated as blacklisted or invalidated
     */
    private boolean failOpen(String message, DataAccessException e) {
        log.warn(message, e);
        Counter.builder(BLACKLIST_CHECK_SKIPPED_COUNTER).register(meterRegistry).increment();
        return false;
    }

    private String blacklistKey(String token) {
        return BLACKLIST_KEY_PREFIX + hashToken(token);
    }

    private String userInvalidationKey(Long userId) {
        return USER_INVALIDATION_KEY_PREFIX + userId;
    }

    private String encode(boolean immediate, long blacklistedAt) {
        return (immediate ? "1" : "0") + ":" + blacklistedAt;
    }

    private boolean decodeImmediate(String value) {
        return value.charAt(0) == '1';
    }

    private long decodeBlacklistedAt(String value) {
        return Long.parseLong(value.substring(value.indexOf(':') + 1));
    }

    /**
     * Hashes a token using SHA-256 to avoid storing raw tokens.
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in Java
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

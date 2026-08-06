package org.danteplanner.backend.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

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

    static final String USER_INVALIDATION_KEY_PREFIX = "uinv:";

    /** Seven days: the refresh-token lifetime a deployment gets when it configures none. */
    public static final long DEFAULT_REFRESH_TOKEN_EXPIRY_MS = 604800000L;

    private static final String BLACKLIST_CHECK_SKIPPED_COUNTER = "blacklist_check_skipped_total";

    /**
     * Argument protocol, where {@code n} is the token count in {@code ARGV[2]}:
     * <ul>
     *   <li>{@code KEYS[1..n]} — the blacklist keys of the tokens being revoked</li>
     *   <li>{@code KEYS[n+1..#KEYS]} — the keys of the families being revoked</li>
     *   <li>{@code ARGV[1]} — the blacklist entry value</li>
     *   <li>{@code ARGV[2]} — {@code n}, the count that splits KEYS</li>
     *   <li>{@code ARGV[3..n+2]} — one TTL per token, in {@code KEYS[1..n]} order</li>
     *   <li>{@code ARGV[n+3]} — the family revocation stamp</li>
     *   <li>{@code ARGV[n+4]} — the family TTL</li>
     * </ul>
     */
    private static final String LOGOUT_REVOKE_SCRIPT =
            "local tokens = tonumber(ARGV[2])\n"
            + "for i = 1, tokens do\n"
            + "  redis.call('SET', KEYS[i], ARGV[1], 'PX', ARGV[2 + i])\n"
            + "end\n"
            + "for i = tokens + 1, #KEYS do\n"
            + "  redis.call('HSET', KEYS[i], '"
            + RefreshRotationService.REVOKED_FIELD + "', ARGV[tokens + 3])\n"
            // Logging out before the session ever rotated creates this hash here, and only the
            // rotation script's sliding PEXPIRE would otherwise ever give it one.
            + "  redis.call('PEXPIRE', KEYS[i], ARGV[tokens + 4])\n"
            + "end\n"
            + "return 'OK'";

    private final DefaultRedisScript<String> logoutRevokeScript =
            new DefaultRedisScript<>(LOGOUT_REVOKE_SCRIPT, String.class);

    /** Bounds the TTL of a user-invalidation entry: no refresh token outlives it. */
    private final long refreshTokenExpiry;

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
            MeterRegistry meterRegistry,
            @Value("${jwt.refresh-token-expiry:" + DEFAULT_REFRESH_TOKEN_EXPIRY_MS + "}")
            long refreshTokenExpiry) {
        this.refreshTokenExpiry = refreshTokenExpiry;
        this.stringRedisTemplate = stringRedisTemplate;
        this.authLocalStringRedisTemplate = authLocalStringRedisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void preloadLogoutRevokeScript() {
        try {
            stringRedisTemplate.execute((RedisCallback<String>) connection ->
                    connection.scriptingCommands().scriptLoad(
                            LOGOUT_REVOKE_SCRIPT.getBytes(StandardCharsets.UTF_8)));
        } catch (DataAccessException e) {
            log.warn("Could not preload logout revocation script; it loads on first use", e);
        }
    }

    /**
     * Withdraws every credential the logout presents, in one atomic Redis round trip.
     *
     * <p>Each revocation names a credential the session actually carried, so a credential the
     * session did not carry is absent from {@code revocations} rather than passed as a null.
     * A token whose lifetime has already lapsed asks for no blacklist entry; when that leaves
     * nothing to write, no Redis command is issued.</p>
     *
     * @param revocations the credentials to withdraw
     */
    public void revokeLogoutSession(@NonNull Collection<LogoutRevocation> revocations) {
        long now = System.currentTimeMillis();

        List<String> tokenKeys = new ArrayList<>();
        List<String> tokenTtls = new ArrayList<>();
        List<String> familyKeys = new ArrayList<>();
        for (LogoutRevocation revocation : revocations) {
            switch (revocation) {
                case LogoutRevocation.TokenRevocation token -> {
                    long remaining = remainingMillis(token.expiry(), now);
                    if (remaining > 0) {
                        tokenKeys.add(blacklistKey(token.token()));
                        tokenTtls.add(Long.toString(remaining));
                    }
                }
                case LogoutRevocation.FamilyRevocation family ->
                        familyKeys.add(RefreshRotationService.familyKey(family.familyId()));
            }
        }

        if (tokenKeys.isEmpty() && familyKeys.isEmpty()) {
            return;
        }

        List<String> keys = new ArrayList<>(tokenKeys);
        keys.addAll(familyKeys);

        List<Object> args = new ArrayList<>();
        args.add(encode(BlacklistMode.IMMEDIATE, now));
        args.add(Long.toString(tokenKeys.size()));
        args.addAll(tokenTtls);
        args.add(Long.toString(now));
        args.add(Long.toString(refreshTokenExpiry));

        stringRedisTemplate.execute(logoutRevokeScript, keys, args.toArray());
    }

    /**
     * The lifetime a token has left, floored at zero so an expired token asks for no blacklist
     * entry at all.
     */
    private long remainingMillis(Date expiry, long now) {
        return Math.max(expiry.getTime() - now, 0);
    }

    /**
     * Adds a token to the blacklist with immediate effect.
     * Used for logout — no grace period, token is rejected instantly.
     *
     * @param token the token to blacklist
     * @param expiry the token's expiration date (entries auto-expire after this)
     */
    public void blacklistToken(String token, Date expiry) {
        addToBlacklist(token, expiry, BlacklistMode.IMMEDIATE);
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
        addToBlacklist(token, expiry, BlacklistMode.ROTATION_GRACE);
    }

    private void addToBlacklist(String token, Date expiry, BlacklistMode mode) {
        long now = System.currentTimeMillis();
        long remaining = remainingMillis(expiry, now);
        if (remaining == 0) {
            return;
        }

        stringRedisTemplate.opsForValue().set(
                blacklistKey(token), encode(mode, now), Duration.ofMillis(remaining));
    }

    /**
     * Checks if a token is blacklisted.
     * Rotation-blacklisted tokens are allowed within the grace period.
     *
     * @param token the token to check
     * @return true if the token is blacklisted and outside any grace period
     */
    public boolean isBlacklisted(String token) {
        try {
            String value = authLocalStringRedisTemplate.opsForValue().get(blacklistKey(token));
            if (value == null) {
                return false;
            }

            return BlacklistMode.of(value) == BlacklistMode.IMMEDIATE
                    || System.currentTimeMillis() - decodeBlacklistedAt(value) >= ROTATION_GRACE_PERIOD_MS;
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
     * @throws NullPointerException if {@code userId} is null
     */
    public void invalidateUserTokens(@NonNull Long userId) {
        Objects.requireNonNull(userId, "userId");
        // Floored to the second because a JWT's iat carries no sub-second component: an
        // unfloored stamp would reject a token minted later in the same second as the
        // invalidation, which is exactly the token a user logging straight back in receives.
        long now = System.currentTimeMillis();
        long stamp = now - (now % 1000);
        stringRedisTemplate.opsForValue().set(
                userInvalidationKey(userId), String.valueOf(stamp), Duration.ofMillis(refreshTokenExpiry));
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
     * @throws NullPointerException if {@code userId} is null
     */
    public void clearUserInvalidation(@NonNull Long userId) {
        Objects.requireNonNull(userId, "userId");
        stringRedisTemplate.delete(userInvalidationKey(userId));
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

    private String encode(BlacklistMode mode, long blacklistedAt) {
        return mode.marker + ":" + blacklistedAt;
    }

    private long decodeBlacklistedAt(String value) {
        return Long.parseLong(value.substring(value.indexOf(':') + 1));
    }

    /**
     * When a blacklist entry starts rejecting the token it names. The marker is the entry's
     * leading character in Redis, so entries written by either mode read back the same way.
     */
    private enum BlacklistMode {

        /** Rejected from the moment it is written. */
        IMMEDIATE('1'),

        /**
         * Tolerated for {@link TokenBlacklistService#ROTATION_GRACE_PERIOD_MS} after it is
         * written, so requests racing a rotation still pass.
         */
        ROTATION_GRACE('0');

        private final char marker;

        BlacklistMode(char marker) {
            this.marker = marker;
        }

        static BlacklistMode of(String encoded) {
            return encoded.charAt(0) == IMMEDIATE.marker ? IMMEDIATE : ROTATION_GRACE;
        }
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

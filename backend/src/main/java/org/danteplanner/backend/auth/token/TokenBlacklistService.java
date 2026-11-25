package org.danteplanner.backend.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import org.danteplanner.backend.shared.redis.AuthReadSources;
import org.danteplanner.backend.shared.redis.AuthWrites;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Tokens are stored as SHA-256 hashes to avoid keeping
 * sensitive token values in Redis. Entries auto-expire via Redis TTL
 * set from the token's original expiration time.</p>
 */
@Service
@Slf4j
public class TokenBlacklistService {

    private enum StoredMark {
        NONE,
        REVOKES;

        private static StoredMark ofBlacklistEntry(@Nullable String entry) {
            return entry == null ? NONE : REVOKES;
        }

        private static StoredMark ofInvalidationStamp(@Nullable String stamp, long issuedAt) {
            return stamp != null && issuedAt < Long.parseLong(stamp) ? REVOKES : NONE;
        }
    }

    private static final String BLACKLIST_VALUE_PREFIX = "1:";

    static final String BLACKLIST_KEY_PREFIX = "bl:";

    static final String USER_INVALIDATION_KEY_PREFIX = "uinv:";

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

    private final long refreshTokenExpiry;

    private final AuthWrites authWrites;

    private final AuthReadSources authReadSources;

    private final Clock clock;

    private final Counter blacklistCheckSkipped;

    public TokenBlacklistService(
            AuthWrites authWrites,
            AuthReadSources authReadSources,
            MeterRegistry meterRegistry,
            Clock clock,
            @Value("${jwt.refresh-token-expiry:" + DEFAULT_REFRESH_TOKEN_EXPIRY_MS + "}")
            long refreshTokenExpiry) {
        this.refreshTokenExpiry = refreshTokenExpiry;
        this.clock = clock;
        this.authWrites = authWrites;
        this.authReadSources = authReadSources;
        this.blacklistCheckSkipped = Counter.builder(BLACKLIST_CHECK_SKIPPED_COUNTER).register(meterRegistry);
    }

    @PostConstruct
    void preloadLogoutRevokeScript() {
        try {
            authWrites.writeWithoutResult(store -> store.execute((RedisCallback<String>) connection ->
                    connection.scriptingCommands().scriptLoad(
                            LOGOUT_REVOKE_SCRIPT.getBytes(StandardCharsets.UTF_8))));
        } catch (DataAccessException e) {
            log.warn("Could not preload logout revocation script; it loads on first use", e);
        }
    }

    public void revokeLogoutSession(@NonNull Collection<LogoutRevocation> revocations) {
        long now = clock.millis();

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
        args.add(encode(now));
        args.add(Long.toString(tokenKeys.size()));
        args.addAll(tokenTtls);
        args.add(Long.toString(now));
        args.add(Long.toString(refreshTokenExpiry));

        authWrites.writeWithoutResult(store -> store.execute(logoutRevokeScript, keys, args.toArray()));
    }

    private long remainingMillis(Date expiry, long now) {
        return Math.max(expiry.getTime() - now, 0);
    }

    public void blacklistToken(String token, Date expiry) {
        long now = clock.millis();
        long remaining = remainingMillis(expiry, now);
        if (remaining == 0) {
            return;
        }

        authWrites.writeWithoutResult(store -> store.opsForValue().set(
                blacklistKey(token), encode(now), Duration.ofMillis(remaining)));
    }

    public RevocationOutcome blacklistCheck(String token) {
        try {
            return authReadSources.read(
                    store -> outcomeOf(StoredMark.ofBlacklistEntry(store.opsForValue().get(blacklistKey(token)))),
                    this::skipped);
        } catch (DataAccessException e) {
            log.warn("Blacklist check failed open due to Redis error", e);
            return skipped();
        }
    }

    public void invalidateUserTokens(@NonNull Long userId) {
        Objects.requireNonNull(userId, "userId");
        // Floored to the second because a JWT's iat carries no sub-second component: an
        // unfloored stamp would reject a token minted later in the same second as the
        // invalidation, which is exactly the token a user logging straight back in receives.
        long now = clock.millis();
        long stamp = now - (now % 1000);
        authWrites.writeWithoutResult(store -> store.opsForValue().set(
                userInvalidationKey(userId), String.valueOf(stamp), Duration.ofMillis(refreshTokenExpiry)));
        log.info("Invalidated all tokens for user {}", userId);
    }

    public RevocationOutcome invalidationCheck(Long userId, long issuedAt) {
        try {
            return authReadSources.read(
                    store -> outcomeOf(StoredMark.ofInvalidationStamp(
                            store.opsForValue().get(userInvalidationKey(userId)), issuedAt)),
                    this::skipped);
        } catch (DataAccessException e) {
            log.warn("User invalidation check failed open due to Redis error", e);
            return skipped();
        }
    }

    private static RevocationOutcome outcomeOf(StoredMark mark) {
        return switch (mark) {
            case NONE -> RevocationOutcome.CLEAR;
            case REVOKES -> RevocationOutcome.REVOKED;
        };
    }

    private RevocationOutcome skipped() {
        blacklistCheckSkipped.increment();
        return RevocationOutcome.SKIPPED;
    }

    private String blacklistKey(String token) {
        return BLACKLIST_KEY_PREFIX + hashToken(token);
    }

    private String userInvalidationKey(Long userId) {
        return USER_INVALIDATION_KEY_PREFIX + userId;
    }

    private String encode(long blacklistedAt) {
        return BLACKLIST_VALUE_PREFIX + blacklistedAt;
    }

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

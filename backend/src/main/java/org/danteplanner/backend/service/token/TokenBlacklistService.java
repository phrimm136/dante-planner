package org.danteplanner.backend.service.token;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token blacklist service with TTL support.
 * Uses ConcurrentHashMap for thread-safe storage.
 *
 * <p>Tokens are stored as SHA-256 hashes to avoid keeping
 * sensitive token values in memory. Entries auto-expire
 * based on the token's original expiration time.</p>
 *
 * <p>Also supports user-level token invalidation via timestamp.
 * When a user is demoted, all their tokens issued before the
 * invalidation time are rejected.</p>
 *
 * <p>Note: This is a single-server solution. For multi-server
 * deployments, consider using Redis or similar distributed cache.</p>
 */
@Service
@Slf4j
public class TokenBlacklistService {

    /**
     * Maximum number of entries in the blacklist.
     * Prevents memory exhaustion from excessive logout requests.
     */
    private static final int MAX_BLACKLIST_SIZE = 100_000;

    /**
     * Grace period for rotation-blacklisted tokens (milliseconds).
     * Concurrent requests carrying the same refresh token are allowed
     * within this window during token rotation. Logout-blacklisted
     * tokens bypass this grace period entirely.
     */
    private static final long ROTATION_GRACE_PERIOD_MS = 5_000;

    /**
     * Refresh token expiry in milliseconds (injected from config).
     * Used to calculate TTL for user invalidation entries.
     */
    @Value("${jwt.refresh-token-expiry:604800000}")
    private long refreshTokenExpiry;

    private record BlacklistEntry(long expiryTime, long blacklistedAt, boolean immediate) {}

    /**
     * Maps token hash to blacklist entry.
     * Entries are lazily cleaned on access.
     */
    private final ConcurrentHashMap<String, BlacklistEntry> blacklist = new ConcurrentHashMap<>();

    /**
     * Maps userId to the timestamp when all their tokens were invalidated.
     * Tokens issued before this timestamp are considered invalid.
     */
    private final ConcurrentHashMap<Long, Long> userInvalidationTimes = new ConcurrentHashMap<>();

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

        // Prevent memory exhaustion
        if (blacklist.size() >= MAX_BLACKLIST_SIZE) {
            cleanupExpired();
            if (blacklist.size() >= MAX_BLACKLIST_SIZE) {
                log.warn("Token blacklist at capacity ({}), forcing cleanup", MAX_BLACKLIST_SIZE);
                long cutoff = System.currentTimeMillis() + 60_000;
                blacklist.entrySet().removeIf(e -> e.getValue().expiryTime() < cutoff);
            }
        }

        String hash = hashToken(token);
        blacklist.put(hash, new BlacklistEntry(expiry.getTime(), System.currentTimeMillis(), immediate));
    }

    /**
     * Checks if a token is blacklisted.
     * Performs lazy cleanup of expired entries.
     *
     * @param token the token to check
     * @return true if the token is blacklisted and not yet expired
     */
    public boolean isBlacklisted(String token) {
        if (token == null) {
            return false;
        }

        String hash = hashToken(token);
        BlacklistEntry entry = blacklist.get(hash);

        if (entry == null) {
            return false;
        }

        long now = System.currentTimeMillis();

        // Lazy cleanup: remove expired entries
        if (now > entry.expiryTime()) {
            blacklist.remove(hash);
            return false;
        }

        // Rotation grace period: allow concurrent requests using the same
        // refresh token within the window. Logout-blacklisted tokens (immediate)
        // bypass this and are rejected instantly.
        if (!entry.immediate() && now - entry.blacklistedAt() < ROTATION_GRACE_PERIOD_MS) {
            return false;
        }

        return true;
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
        userInvalidationTimes.put(userId, System.currentTimeMillis());
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
        Long invalidationTime = userInvalidationTimes.get(userId);
        return invalidationTime != null && issuedAt < invalidationTime;
    }

    /**
     * Clears user invalidation entry (for testing or when user re-authenticates).
     *
     * @param userId the user whose invalidation should be cleared
     */
    public void clearUserInvalidation(Long userId) {
        if (userId != null) {
            userInvalidationTimes.remove(userId);
        }
    }

    /**
     * Returns the current size of the blacklist.
     * Useful for monitoring and testing.
     */
    public int size() {
        return blacklist.size();
    }

    /**
     * Returns the number of users with invalidated tokens.
     * Useful for monitoring and testing.
     */
    public int userInvalidationSize() {
        return userInvalidationTimes.size();
    }

    /**
     * Clears all entries from the blacklist and user invalidations.
     * Primarily for testing purposes.
     */
    public void clear() {
        blacklist.clear();
        userInvalidationTimes.clear();
    }

    /**
     * Performs cleanup of all expired entries.
     * Scheduled to run every hour to prevent memory buildup.
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void cleanupExpired() {
        int sizeBefore = blacklist.size();
        long now = System.currentTimeMillis();
        blacklist.entrySet().removeIf(entry -> entry.getValue().expiryTime() < now);
        int removed = sizeBefore - blacklist.size();
        if (removed > 0) {
            log.debug("Token blacklist cleanup: removed {} expired entries, {} remaining", removed, blacklist.size());
        }
    }

    /**
     * Performs cleanup of stale user invalidation entries.
     * Entries older than refresh token expiry + 1 hour buffer are removed.
     * Scheduled to run every 6 hours.
     */
    @Scheduled(fixedRate = 21600000) // Every 6 hours
    public void cleanupUserInvalidations() {
        int sizeBefore = userInvalidationTimes.size();
        // TTL = refresh token expiry + 1 hour buffer
        long ttl = refreshTokenExpiry + 3600000;
        long cutoff = System.currentTimeMillis() - ttl;
        userInvalidationTimes.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        int removed = sizeBefore - userInvalidationTimes.size();
        if (removed > 0) {
            log.info("User invalidation cleanup: removed {} stale entries, {} remaining", removed, userInvalidationTimes.size());
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

package org.danteplanner.backend.support;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.test.util.ReflectionTestUtils;

import org.danteplanner.backend.auth.token.LogoutRevocation;
import org.danteplanner.backend.auth.token.TokenBlacklistService;

public class RecordingTokenBlacklist extends TokenBlacklistService {

    private static final long ROTATION_GRACE_PERIOD_MS =
            (long) ReflectionTestUtils.getField(TokenBlacklistService.class, "ROTATION_GRACE_PERIOD_MS");

    public enum Operation {
        REVOKE_LOGOUT_SESSION,
        BLACKLIST_TOKEN,
        BLACKLIST_TOKEN_FOR_ROTATION,
        INVALIDATE_USER_TOKENS,
        CLEAR_USER_INVALIDATION
    }

    private enum Mode {
        IMMEDIATE,
        ROTATION_GRACE
    }

    private record Entry(Mode mode, long blacklistedAt, long expiresAt) {
    }

    private record Invalidation(long stamp, long expiresAt) {
    }

    public record Failed(Operation operation, RuntimeException failure) {
    }

    private final Clock clock;
    private final List<LogoutRevocation> revocations = new ArrayList<>();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<Long, Invalidation> invalidations = new LinkedHashMap<>();
    private final List<Failed> failed = new ArrayList<>();
    private final ScriptedFailures<Operation> failures =
            new ScriptedFailures<>(RecordingTokenBlacklist.class.getSimpleName(), Operation.class);

    public RecordingTokenBlacklist(Clock clock) {
        super(null, null, null, DEFAULT_REFRESH_TOKEN_EXPIRY_MS);
        this.clock = clock;
    }

    public void failNext(Operation operation, RuntimeException failure) {
        failures.failNext(operation, failure);
    }

    public List<Failed> failed() {
        return List.copyOf(failed);
    }

    public List<LogoutRevocation> revocations() {
        return List.copyOf(revocations);
    }

    public Set<String> blacklistedTokens() {
        return entries.keySet().stream().filter(this::isBlacklisted).collect(Collectors.toUnmodifiableSet());
    }

    public Set<Long> invalidatedUserIds() {
        long now = clock.millis();
        return invalidations.entrySet().stream()
                .filter(invalidation -> invalidation.getValue().expiresAt() > now)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void revokeLogoutSession(Collection<LogoutRevocation> revocations) {
        failIfScripted(Operation.REVOKE_LOGOUT_SESSION);
        this.revocations.addAll(revocations);
        for (LogoutRevocation revocation : revocations) {
            if (revocation instanceof LogoutRevocation.TokenRevocation token) {
                put(token.token(), token.expiry(), Mode.IMMEDIATE);
            }
        }
    }

    @Override
    public void blacklistToken(String token, Date expiry) {
        failIfScripted(Operation.BLACKLIST_TOKEN);
        put(token, expiry, Mode.IMMEDIATE);
    }

    @Override
    public void blacklistTokenForRotation(String token, Date expiry) {
        failIfScripted(Operation.BLACKLIST_TOKEN_FOR_ROTATION);
        put(token, expiry, Mode.ROTATION_GRACE);
    }

    @Override
    public boolean isBlacklisted(String token) {
        long now = clock.millis();
        Entry entry = entries.get(token);
        if (entry == null || entry.expiresAt() <= now) {
            return false;
        }
        return entry.mode() == Mode.IMMEDIATE || now - entry.blacklistedAt() >= ROTATION_GRACE_PERIOD_MS;
    }

    @Override
    public void invalidateUserTokens(Long userId) {
        Objects.requireNonNull(userId, "userId");
        failIfScripted(Operation.INVALIDATE_USER_TOKENS);
        long now = clock.millis();
        invalidations.put(userId, new Invalidation(now - (now % 1000), now + DEFAULT_REFRESH_TOKEN_EXPIRY_MS));
    }

    @Override
    public boolean isUserTokenInvalidated(Long userId, long issuedAt) {
        Invalidation invalidation = invalidations.get(userId);
        return invalidation != null && invalidation.expiresAt() > clock.millis() && issuedAt < invalidation.stamp();
    }

    @Override
    public void clearUserInvalidation(Long userId) {
        Objects.requireNonNull(userId, "userId");
        failIfScripted(Operation.CLEAR_USER_INVALIDATION);
        invalidations.remove(userId);
    }

    @Override
    public int size() {
        long now = clock.millis();
        return (int) entries.values().stream().filter(entry -> entry.expiresAt() > now).count();
    }

    @Override
    public int userInvalidationSize() {
        return invalidatedUserIds().size();
    }

    @Override
    public void clear() {
        entries.clear();
        invalidations.clear();
    }

    private void put(String token, Date expiry, Mode mode) {
        long now = clock.millis();
        if (expiry.getTime() > now) {
            entries.put(token, new Entry(mode, now, expiry.getTime()));
        }
    }

    private void failIfScripted(Operation operation) {
        failures.throwIfScripted(operation, failure -> failed.add(new Failed(operation, failure)));
    }
}

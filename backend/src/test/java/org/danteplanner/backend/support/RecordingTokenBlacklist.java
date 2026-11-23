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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.auth.token.LogoutRevocation;
import org.danteplanner.backend.auth.token.TokenBlacklistService;

public class RecordingTokenBlacklist extends TokenBlacklistService {

    public enum Operation {
        REVOKE_LOGOUT_SESSION,
        BLACKLIST_TOKEN,
        INVALIDATE_USER_TOKENS
    }

    private record Invalidation(long stamp, long expiresAt) {
    }

    public record Failed(Operation operation, RuntimeException failure) {
    }

    private final Clock clock;
    private final List<LogoutRevocation> revocations = new ArrayList<>();
    private final Map<String, Long> expiries = new LinkedHashMap<>();
    private final Map<Long, Invalidation> invalidations = new LinkedHashMap<>();
    private final List<Failed> failed = new ArrayList<>();
    private final ScriptedFailures<Operation> failures =
            new ScriptedFailures<>(RecordingTokenBlacklist.class.getSimpleName(), Operation.class);

    public RecordingTokenBlacklist(Clock clock) {
        super(null, null, new SimpleMeterRegistry(), clock, DEFAULT_REFRESH_TOKEN_EXPIRY_MS);
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
        return expiries.keySet().stream().filter(this::isBlacklisted).collect(Collectors.toUnmodifiableSet());
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
                put(token.token(), token.expiry());
            }
        }
    }

    @Override
    public void blacklistToken(String token, Date expiry) {
        failIfScripted(Operation.BLACKLIST_TOKEN);
        put(token, expiry);
    }

    @Override
    public boolean isBlacklisted(String token) {
        Long expiresAt = expiries.get(token);
        return expiresAt != null && expiresAt > clock.millis();
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

    public int size() {
        long now = clock.millis();
        return (int) expiries.values().stream().filter(expiresAt -> expiresAt > now).count();
    }

    public int userInvalidationSize() {
        return invalidatedUserIds().size();
    }

    private void put(String token, Date expiry) {
        if (expiry.getTime() > clock.millis()) {
            expiries.put(token, expiry.getTime());
        }
    }

    private void failIfScripted(Operation operation) {
        failures.throwIfScripted(operation, failure -> failed.add(new Failed(operation, failure)));
    }
}

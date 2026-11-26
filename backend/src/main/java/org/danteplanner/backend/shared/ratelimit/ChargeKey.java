package org.danteplanner.backend.shared.ratelimit;

import org.danteplanner.backend.shared.failure.Parsed;

@Parsed
public record ChargeKey(String subject, String endpoint, String refusedUser) {

    private static final String NO_USER = "null";

    public static ChargeKey parseClient(RateLimitPolicy policy, String clientIdentifier) {
        return new ChargeKey(clientIdentifier, fixedEndpoint(policy), NO_USER);
    }

    public static ChargeKey parseUser(RateLimitPolicy policy, long userId, String declaredEndpoint) {
        String endpoint = declaredEndpoint.isEmpty() ? fixedEndpoint(policy) : declaredEndpoint;
        return new ChargeKey(String.valueOf(userId), endpoint, String.valueOf(userId));
    }

    public String spelled() {
        return subject + ":" + endpoint;
    }

    public RateLimitExceededException refusal(long retryAfterSeconds) {
        return new RateLimitExceededException(refusedUser, endpoint, retryAfterSeconds);
    }

    private static String fixedEndpoint(RateLimitPolicy policy) {
        return switch (policy.endpoint()) {
            case RateLimitPolicy.Endpoint.Fixed fixed -> fixed.name();
            case RateLimitPolicy.Endpoint.CallerNamed callerNamed ->
                    throw new IllegalArgumentException(policy + " keys its buckets by a caller-named endpoint");
        };
    }
}

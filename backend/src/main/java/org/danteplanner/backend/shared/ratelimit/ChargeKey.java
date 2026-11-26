package org.danteplanner.backend.shared.ratelimit;

import org.danteplanner.backend.shared.failure.Parsed;

public sealed interface ChargeKey {

    String spelled();

    RateLimitExceededException refusal(long retryAfterSeconds);

    @Parsed
    record Client(String identifier, String endpoint) implements ChargeKey {

        public static Client parse(RateLimitPolicy policy, String clientIdentifier) {
            return new Client(clientIdentifier, fixedEndpoint(policy));
        }

        @Override
        public String spelled() {
            return identifier + ":" + endpoint;
        }

        @Override
        public RateLimitExceededException refusal(long retryAfterSeconds) {
            return new RateLimitExceededException("client", endpoint, retryAfterSeconds);
        }
    }

    @Parsed
    record User(long userId, String endpoint) implements ChargeKey {

        public static User parse(RateLimitPolicy policy, long userId, String declaredEndpoint) {
            return new User(userId, declaredEndpoint.isEmpty() ? fixedEndpoint(policy) : declaredEndpoint);
        }

        @Override
        public String spelled() {
            return userId + ":" + endpoint;
        }

        @Override
        public RateLimitExceededException refusal(long retryAfterSeconds) {
            return new RateLimitExceededException("user " + userId, endpoint, retryAfterSeconds);
        }
    }

    private static String fixedEndpoint(RateLimitPolicy policy) {
        return switch (policy.endpoint()) {
            case RateLimitPolicy.Endpoint.Fixed fixed -> fixed.name();
            case RateLimitPolicy.Endpoint.CallerNamed callerNamed ->
                    throw new IllegalArgumentException(policy + " keys its buckets by a caller-named endpoint");
        };
    }
}

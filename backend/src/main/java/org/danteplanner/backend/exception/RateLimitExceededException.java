package org.danteplanner.backend.exception;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {

    private final Long userId;
    private final String endpoint;

    public RateLimitExceededException(Long userId, String endpoint) {
        super("Rate limit exceeded for user " + userId + " on endpoint " + endpoint);
        this.userId = userId;
        this.endpoint = endpoint;
    }
}

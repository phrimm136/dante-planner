package org.danteplanner.backend.shared.ratelimit;

import lombok.Getter;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

@Getter
public class RateLimitExceededException extends DomainException {

    private static final String ERROR_CODE = "RATE_LIMIT_EXCEEDED";

    private final Long userId;
    private final String endpoint;

    public RateLimitExceededException(Long userId, String endpoint) {
        super(ErrorKind.OVER_QUOTA, ERROR_CODE,
                "Rate limit exceeded for user " + userId + " on endpoint " + endpoint);
        this.userId = userId;
        this.endpoint = endpoint;
    }
}

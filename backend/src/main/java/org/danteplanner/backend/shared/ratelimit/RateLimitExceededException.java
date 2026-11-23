package org.danteplanner.backend.shared.ratelimit;

import org.springframework.http.HttpHeaders;

import lombok.Getter;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

@Getter
public class RateLimitExceededException extends DomainException {

    private static final String ERROR_CODE = "RATE_LIMIT_EXCEEDED";

    private final String endpoint;

    public RateLimitExceededException(String user, String endpoint, long retryAfterSeconds) {
        super(ErrorKind.OVER_QUOTA, ERROR_CODE,
                "Rate limit exceeded for user " + user + " on endpoint " + endpoint);
        this.endpoint = endpoint;
        getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
    }
}

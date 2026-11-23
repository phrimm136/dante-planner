package org.danteplanner.backend.shared.exception;

import org.springframework.http.HttpHeaders;

public class ReadTemporarilyUnavailableException extends DomainException {

    public ReadTemporarilyUnavailableException() {
        super(ErrorKind.UNAVAILABLE, DegradationErrorConstants.READ_UNAVAILABLE.code(),
                DegradationErrorConstants.READ_UNAVAILABLE.message());
        retryLater();
    }

    public ReadTemporarilyUnavailableException(Throwable cause) {
        super(ErrorKind.UNAVAILABLE, DegradationErrorConstants.READ_UNAVAILABLE.code(),
                DegradationErrorConstants.READ_UNAVAILABLE.message(), cause);
        retryLater();
    }

    private void retryLater() {
        getHeaders().set(HttpHeaders.RETRY_AFTER, DegradationErrorConstants.RETRY_AFTER_SECONDS);
    }
}

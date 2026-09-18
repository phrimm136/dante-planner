package org.danteplanner.backend.user.exception;

import lombok.Getter;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

import java.time.Instant;

@Getter
public class UserTimedOutException extends DomainException {

    private static final String ERROR_CODE = "USER_TIMED_OUT";

    private final Long userId;
    private final Instant timeoutUntil;

    public UserTimedOutException(Long userId, Instant timeoutUntil) {
        super(ErrorKind.FORBIDDEN, ERROR_CODE,
                "Your account is temporarily restricted until " + timeoutUntil);
        this.userId = userId;
        this.timeoutUntil = timeoutUntil;
    }

    @Override
    public String getMessage() {
        return "User " + userId + " is timed out until " + timeoutUntil;
    }
}

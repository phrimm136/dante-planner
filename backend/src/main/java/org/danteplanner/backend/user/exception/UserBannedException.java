package org.danteplanner.backend.user.exception;

import lombok.Getter;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

import java.time.Instant;

/**
 * Exception thrown when a banned user attempts a write operation.
 */
@Getter
public class UserBannedException extends DomainException {

    private static final String ERROR_CODE = "USER_BANNED";
    private static final String CLIENT_DETAIL = "Your account has been suspended";

    private final Long userId;
    private final Instant bannedAt;

    public UserBannedException(Long userId, Instant bannedAt) {
        super(ErrorKind.FORBIDDEN, ERROR_CODE, CLIENT_DETAIL);
        this.userId = userId;
        this.bannedAt = bannedAt;
    }

    @Override
    public String getMessage() {
        return "User " + userId + " is banned since " + bannedAt;
    }
}

package org.danteplanner.backend.user.exception;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

import lombok.Getter;

@Getter
public class UserNotFoundException extends DomainException {

    private final Long userId;
    private final String usernameSuffix;

    public UserNotFoundException(Long userId) {
        super(ErrorKind.NOT_FOUND, "USER_NOT_FOUND", "User not found with id: " + userId);
        this.userId = userId;
        this.usernameSuffix = null;
    }

    public UserNotFoundException(String usernameSuffix) {
        super(ErrorKind.NOT_FOUND, "USER_NOT_FOUND", "User not found with suffix: " + usernameSuffix);
        this.userId = null;
        this.usernameSuffix = usernameSuffix;
    }
}

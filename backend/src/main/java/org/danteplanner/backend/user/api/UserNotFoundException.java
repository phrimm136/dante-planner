package org.danteplanner.backend.user.api;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;


public class UserNotFoundException extends DomainException {

    public UserNotFoundException(Long userId) {
        super(ErrorKind.NOT_FOUND, "USER_NOT_FOUND", "User not found with id: " + userId);
    }

    public UserNotFoundException(String usernameSuffix) {
        super(ErrorKind.NOT_FOUND, "USER_NOT_FOUND", "User not found with suffix: " + usernameSuffix);
    }
}

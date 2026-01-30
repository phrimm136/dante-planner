package org.danteplanner.backend.exception;

import lombok.Getter;

@Getter
public class UserNotFoundException extends RuntimeException {

    private final Long userId;
    private final String usernameSuffix;

    public UserNotFoundException(Long userId) {
        super("User not found with id: " + userId);
        this.userId = userId;
        this.usernameSuffix = null;
    }

    public UserNotFoundException(String usernameSuffix) {
        super("User not found with suffix: " + usernameSuffix);
        this.userId = null;
        this.usernameSuffix = usernameSuffix;
    }
}

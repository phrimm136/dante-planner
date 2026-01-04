package org.danteplanner.backend.exception;

import lombok.Getter;

@Getter
public class AccountDeletedException extends RuntimeException {

    private final Long userId;

    public AccountDeletedException(Long userId) {
        super("Account with ID " + userId + " has been deleted");
        this.userId = userId;
    }
}

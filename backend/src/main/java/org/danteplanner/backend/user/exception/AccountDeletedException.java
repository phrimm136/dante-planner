package org.danteplanner.backend.user.exception;

import lombok.Getter;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

@Getter
public class AccountDeletedException extends DomainException {

    private static final String ERROR_CODE = "UNAUTHORIZED";
    private static final String CLIENT_DETAIL = "Authentication required";

    private final Long userId;

    public AccountDeletedException(Long userId) {
        super(ErrorKind.UNAUTHENTICATED, ERROR_CODE, CLIENT_DETAIL,
                "Account with ID " + userId + " has been deleted", null);
        this.userId = userId;
    }

    @Override
    public boolean reportable() {
        return true;
    }
}

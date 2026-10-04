package org.danteplanner.backend.user.exception;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

public class UserForbiddenException extends DomainException {

    public UserForbiddenException(String message) {
        super(ErrorKind.FORBIDDEN, "FORBIDDEN", message);
    }
}

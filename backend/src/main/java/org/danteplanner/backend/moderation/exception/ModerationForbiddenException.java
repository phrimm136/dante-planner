package org.danteplanner.backend.moderation.exception;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

public class ModerationForbiddenException extends DomainException {

    public ModerationForbiddenException(String message) {
        super(ErrorKind.FORBIDDEN, "FORBIDDEN", message);
    }
}

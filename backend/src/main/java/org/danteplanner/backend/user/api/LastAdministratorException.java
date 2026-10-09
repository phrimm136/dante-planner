package org.danteplanner.backend.user.api;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

public class LastAdministratorException extends DomainException {

    public LastAdministratorException() {
        super(ErrorKind.CONFLICT, "LAST_ADMINISTRATOR", "Another active administrator must remain");
    }
}

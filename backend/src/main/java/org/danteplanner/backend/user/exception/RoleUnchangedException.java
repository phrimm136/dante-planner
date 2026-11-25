package org.danteplanner.backend.user.exception;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;
import org.danteplanner.backend.user.api.UserRole;

public class RoleUnchangedException extends DomainException {

    public RoleUnchangedException(UserRole role) {
        super(ErrorKind.CONFLICT, "ROLE_UNCHANGED", "User already has the role " + role);
    }
}

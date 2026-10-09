package org.danteplanner.backend.user.validation;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.user.api.LastAdministratorException;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.exception.UserForbiddenException;
import org.danteplanner.backend.user.repository.UserRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoleChangeValidator {

    private final UserRepository userRepository;

    public void requireCanChangeRole(User actor, User target, UserRole newRole) {
        if (newRole.outranks(actor.getRole())) {
            throw new UserForbiddenException("Cannot grant role higher than your own");
        }

        boolean self = actor.getId().equals(target.getId());
        if (!self && target.getRole().hasRankAtLeast(actor.getRole())) {
            throw new UserForbiddenException("Cannot modify user of equal or higher rank");
        }

        if (target.getRole() == UserRole.ADMIN && newRole != UserRole.ADMIN) {
            requireAnotherActiveAdministrator();
        }
    }

    public void requireAnotherActiveAdministrator() {
        if (userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN) <= 1) {
            throw new LastAdministratorException();
        }
    }
}

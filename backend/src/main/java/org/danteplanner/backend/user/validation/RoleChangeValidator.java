package org.danteplanner.backend.user.validation;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.failure.Parsed;
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

    @Parsed
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class AllowedRoleChange {

        private final User target;
        private final UserRole newRole;

        private static AllowedRoleChange of(RankDecision.Allowed allowed) {
            return new AllowedRoleChange(allowed.target(), allowed.newRole());
        }

        private static AllowedRoleChange of(
                RankDecision.DemotesAdministrator demotion, AnotherActiveAdministrator remaining) {
            return new AllowedRoleChange(demotion.target(), demotion.newRole());
        }
    }

    @Parsed
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class AnotherActiveAdministrator {

        private static AnotherActiveAdministrator of(long activeAdministrators) {
            if (activeAdministrators <= 1) {
                throw new LastAdministratorException();
            }
            return new AnotherActiveAdministrator();
        }
    }

    private enum RankRefusal {
        GRANT_ABOVE_OWN("Cannot grant role higher than your own"),
        TARGET_NOT_BELOW("Cannot modify user of equal or higher rank");

        private final String message;

        RankRefusal(String message) {
            this.message = message;
        }
    }

    private sealed interface RankDecision {

        record Refused(RankRefusal refusal) implements RankDecision {
        }

        record Allowed(User target, UserRole newRole) implements RankDecision {
        }

        record DemotesAdministrator(User target, UserRole newRole) implements RankDecision {
        }

        static RankDecision of(User actor, User target, UserRole newRole) {
            if (newRole.outranks(actor.getRole())) {
                return new Refused(RankRefusal.GRANT_ABOVE_OWN);
            }
            boolean self = actor.getId().equals(target.getId());
            if (!self && target.getRole().hasRankAtLeast(actor.getRole())) {
                return new Refused(RankRefusal.TARGET_NOT_BELOW);
            }
            if (target.getRole() == UserRole.ADMIN && newRole != UserRole.ADMIN) {
                return new DemotesAdministrator(target, newRole);
            }
            return new Allowed(target, newRole);
        }
    }

    public AllowedRoleChange requireCanChangeRole(User actor, User target, UserRole newRole) {
        return switch (RankDecision.of(actor, target, newRole)) {
            case RankDecision.Refused refused -> throw new UserForbiddenException(refused.refusal().message);
            case RankDecision.Allowed allowed -> AllowedRoleChange.of(allowed);
            case RankDecision.DemotesAdministrator demotion ->
                    AllowedRoleChange.of(demotion, requireAnotherActiveAdministrator());
        };
    }

    public AnotherActiveAdministrator requireAnotherActiveAdministrator() {
        return AnotherActiveAdministrator.of(userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN));
    }
}

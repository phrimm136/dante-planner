package org.danteplanner.backend.user.validation;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.failure.Parsed;
import org.danteplanner.backend.user.api.LastAdministratorException;
import org.danteplanner.backend.user.api.RoleShift;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.exception.RoleUnchangedException;
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

        private static AllowedRoleChange of(RankDecision.DemotesAdministrator demotion, long activeAdministrators) {
            refuseLastAdministrator(activeAdministrators);
            return new AllowedRoleChange(demotion.target(), demotion.newRole());
        }
    }

    @Parsed
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class DeletableAccount {

        private final Long userId;

        private static DeletableAccount of(User user) {
            return new DeletableAccount(user.getId());
        }

        private static DeletableAccount ofAdministrator(User user, long activeAdministrators) {
            refuseLastAdministrator(activeAdministrators);
            return new DeletableAccount(user.getId());
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

        record Unchanged(UserRole role) implements RankDecision {
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
            return switch (RoleShift.of(target.getRole(), newRole)) {
                case SAME -> new Unchanged(newRole);
                case PROMOTED -> new Allowed(target, newRole);
                case DEMOTED -> target.getRole() == UserRole.ADMIN
                        ? new DemotesAdministrator(target, newRole)
                        : new Allowed(target, newRole);
            };
        }
    }

    public AllowedRoleChange requireCanChangeRole(User actor, User target, UserRole newRole) {
        return switch (RankDecision.of(actor, target, newRole)) {
            case RankDecision.Refused refused -> throw new UserForbiddenException(refused.refusal().message);
            case RankDecision.Unchanged unchanged -> throw new RoleUnchangedException(unchanged.role());
            case RankDecision.Allowed allowed -> AllowedRoleChange.of(allowed);
            case RankDecision.DemotesAdministrator demotion ->
                    AllowedRoleChange.of(demotion, activeAdministrators());
        };
    }

    public DeletableAccount requireDeletable(User user) {
        return user.getRole() == UserRole.ADMIN
                ? DeletableAccount.ofAdministrator(user, activeAdministrators())
                : DeletableAccount.of(user);
    }

    private long activeAdministrators() {
        return userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN);
    }

    private static void refuseLastAdministrator(long activeAdministrators) {
        if (activeAdministrators <= 1) {
            throw new LastAdministratorException();
        }
    }
}

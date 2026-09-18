package org.danteplanner.backend.moderation.service;

import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.exception.ModerationForbiddenException;
import org.danteplanner.backend.user.entity.RestrictionState;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Clock;
import java.util.Map;

@Service
public class ModerationPolicy {

    private record Authority(UserRole minimumActorRole, String power, String verb) {}

    private static final Map<ModerationAction.ActionType, Authority> AUTHORITIES = Map.of(
            ModerationAction.ActionType.TIMEOUT,
            new Authority(UserRole.MODERATOR, "timeout users", "timeout"),
            ModerationAction.ActionType.CLEAR_TIMEOUT,
            new Authority(UserRole.MODERATOR, "clear timeouts", "clear the timeout of"),
            ModerationAction.ActionType.BAN,
            new Authority(UserRole.ADMIN, "ban users", "ban"),
            ModerationAction.ActionType.UNBAN,
            new Authority(UserRole.ADMIN, "unban users", "unban"));

    private static final Map<RestrictionState, String> RESTRICTION_ADJECTIVE = Map.of(
            RestrictionState.TIMED_OUT, "timed-out",
            RestrictionState.BANNED, "banned");

    private static final Map<UserRole, String> ROLE_PLURAL = Map.of(
            UserRole.NORMAL, "users",
            UserRole.MODERATOR, "moderators",
            UserRole.ADMIN, "administrators");

    private final Clock clock;

    @Autowired
    public ModerationPolicy() {
        this(Clock.systemUTC());
    }

    ModerationPolicy(Clock clock) {
        this.clock = clock;
    }

    public void requireCanRestrict(User actor, User target, ModerationAction.ActionType action) {
        Authority authority = AUTHORITIES.get(action);
        Assert.notNull(authority, () -> "Not an account restriction: " + action);

        // A restriction withdraws the authority, and nothing else does: banning a rogue moderator
        // invalidates no token, and the endpoint is gated on the role claim the token still carries.
        RestrictionState actorRestriction = actor.restrictionState(clock);
        if (actorRestriction != RestrictionState.ACTIVE) {
            throw new ModerationForbiddenException("A %s account cannot %s"
                    .formatted(RESTRICTION_ADJECTIVE.get(actorRestriction), authority.power()));
        }

        if (!actor.getRole().hasRankAtLeast(authority.minimumActorRole())) {
            throw new ModerationForbiddenException("Only %s can %s"
                    .formatted(ROLE_PLURAL.get(authority.minimumActorRole()), authority.power()));
        }

        if (!actor.getRole().outranks(target.getRole())) {
            throw new ModerationForbiddenException(
                    "Cannot %s a user of equal or higher rank".formatted(authority.verb()));
        }
    }

    public boolean demotesAnAdministrator(UserRole currentRole, UserRole newRole) {
        return currentRole == UserRole.ADMIN && newRole != UserRole.ADMIN;
    }

    public void requireAnotherAdministratorRemains(long administratorCount) {
        if (administratorCount <= 1) {
            throw new ModerationForbiddenException("Cannot demote the last administrator");
        }
    }

    public void requireCanChangeRole(User actor, User target, UserRole newRole) {
        if (newRole.outranks(actor.getRole())) {
            throw new ModerationForbiddenException("Cannot grant role higher than your own");
        }

        boolean self = actor.getId().equals(target.getId());
        if (!self && target.getRole().hasRankAtLeast(actor.getRole())) {
            throw new ModerationForbiddenException("Cannot modify user of equal or higher rank");
        }
    }
}

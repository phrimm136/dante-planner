package org.danteplanner.backend.moderation.service;

import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.exception.ModerationForbiddenException;
import org.danteplanner.backend.user.entity.RestrictionState;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;

/**
 * Who may exercise which authority over whom.
 *
 * <p>Every account-level moderation decision resolves through the same restriction and rank
 * checks, so an authority hole cannot open in one mutation while its siblings stay closed.</p>
 */
@Service
public class ModerationPolicy {

    /**
     * What one account restriction demands.
     *
     * @param minimumActorRole the lowest rank that may exercise the restriction at all
     * @param power            the restriction named as a power ("ban users")
     * @param verb             the restriction named as an act on one user ("ban")
     */
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

    /**
     * Require that the actor may place or lift the named restriction on the target.
     *
     * @param actor  the moderator or admin performing the action
     * @param target the account the restriction lands on
     * @param action the restriction being placed or lifted
     * @throws ModerationForbiddenException if the actor's own account is restricted, if the actor's
     *                                      rank does not carry the authority, or if the actor does
     *                                      not strictly outrank the target
     * @throws IllegalArgumentException     if the action is not an account restriction
     */
    public void requireCanRestrict(User actor, User target, ModerationAction.ActionType action) {
        Authority authority = AUTHORITIES.get(action);
        if (authority == null) {
            throw new IllegalArgumentException("Not an account restriction: " + action);
        }

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

    /**
     * Require that the actor may move the target to the given role.
     *
     * @param actor   the admin performing the change
     * @param target  the account whose role changes
     * @param newRole the role being assigned
     * @throws ModerationForbiddenException if the new role outranks the actor, or the target holds a
     *                                      rank the actor may not modify
     */
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

# 173 the-user-module-owns-role-change-rules-and-the-last-admin-invariant
epic: none · pr: none @roles @admin @modules @isolation

## Decisions
- @roles @modules — The user module owns the role-change rules and the rule that an active
  administrator must remain: `user.validation.RoleChangeValidator` holds them, and
  `Users.changeRole(actorId, targetId, newRole)` runs them before it writes, so no caller can change a
  role without them. The rules had moved into moderation only because a ratchet limited which classes
  may throw, then were published as `RoleChangePolicy`; the count came from user, the threshold from
  moderation and the transaction from admin, and account withdrawal, which lives in user, could not
  reach the rule because moderation already depends on user.
  REJECTED: merging the admin endpoints into moderation and letting user declare a withdrawal hook
  that moderation implements — the rule stays away from the data it guards, an unwired hook lets
  withdrawal through, and the administration prefix is not moderation's (ADR 148).
  REJECTED: a bottom authority module owning role, rank and the privileged-route rules — the target
  shape, but it needs the role column's ownership moved and a decision on ADR 170's restriction move.
  REJECTED: keeping a shallow check the caller feeds with a count — the caller computed the wrong
  count (withdrawn administrators included).
  INVARIANT: the only path that changes a role is `Users.changeRole`, and it refuses a change that
  would leave no active administrator — `RoleChangeValidatorTest`, `UsersRoleChangeContract` (fake and
  MySQL).
- @admin @isolation — An administrator cannot withdraw while no other active administrator remains,
  and withdrawal runs SERIALIZABLE like role changes (ADR 001), so a withdrawal and a demotion of the
  last two administrators cannot both commit. Both refusals answer 409 with the code
  `LAST_ADMINISTRATOR`: the request is valid and becomes possible once another administrator exists,
  which a 403 would misstate. "Active" means not withdrawn, as everywhere else in this codebase.
  REJECTED: the default isolation on withdrawal — correct only through snapshot timing that no test
  could pin.
  INVARIANT: an active administrator always remains after any committed role change or withdrawal —
  `AccountWithdrawalLastAdministratorIT`, `AdminWithdrawalWriteSkewIT`, `AdminRoleWriteSkewIT`.
  NON-GOAL: guarding the hard-delete purge — the account is already withdrawn by then.

## Takeaway
- takeaway: a rule about a module's own data belongs in that module; a ratchet that decides where an
  exception may be thrown must not decide which module owns the rule.

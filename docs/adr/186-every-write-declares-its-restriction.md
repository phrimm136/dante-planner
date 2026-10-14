# 186 every-write-declares-its-restriction
epic: none · pr: none

## Decisions
- @moderation @restriction @coverage — Every write handler declares exactly one of `@Restricted`,
  `@RestrictionExempt(reason)` or `@RestrictedByState`, and an architecture rule fails the build
  when a handler declares none or more than one. Restriction checks placed by hand were missing
  from planner create, subscribe, and the timeout half of both votes and both reports, with nothing
  to detect the absence. `@RestrictionExempt` targets methods only, so a handler added to a class
  later must state its own exemption. A `@Restricted` handler's check runs inside the request's
  write transaction before it commits. A `@RestrictedByState` handler's service decides from the
  aggregate's state and calls the check itself (ADR 078); a request on such a handler that commits
  without that call throws in tests. The annotations and the transaction hook are shared, like
  `@RateLimited`, and the hook calls a port that shared declares; moderation implements it and
  publishes the same check to the services that decide from state, because moderation decides
  restrictions and shared depends on no module.
  REJECTED: the check in the shared package beside the annotations — it would read state another
  module owns, which the published-API rule (ADR 169) forbids.
  REJECTED: the shared hook calling moderation's API directly — shared is the platform and depends
  on no feature module.
  REJECTED: keeping hand-placed checks — an absent check is invisible, which is how the gaps above
  arose.
  REJECTED: deriving restriction from the freshness scopes a write declares — it is enforced only
  on paths a test exercises, so an untested handler is silently unguarded.
  REJECTED: endpoint classification alone — a planner upsert is public only when its target is
  published, which no route-level label can see (ADR 078).
  REJECTED: a check in an interceptor before the transaction — it borrows a second connection for
  a read the write transaction can make on its own.
  INVARIANT: every write handler declares exactly one restriction annotation — the restriction
  coverage architecture rule.
  INVARIANT: a `@RestrictedByState` request never commits without a decision — a test-mode
  assertion at commit.
- @moderation @restriction @policy — A ban or a timeout blocks every write that other people see
  or interact with: publishing, editing a published planner, creating, replying to or editing a
  comment, upvoting a planner or a comment, reporting, and subscribing. It leaves the restricted
  user's own and private actions open: private planner work, unpublishing (ADR 078), deleting their
  own planner or comment, settings, the notification inbox and its toggles, account deletion, and
  choosing an epithet, which comes from a fixed list of harmless phrases.
  REJECTED: blocking withdrawals while restricted — it leaves a restricted user unable to take
  down their own public content.
  REJECTED: blocking the epithet — others see it, but every value is pre-approved, so nothing a
  restriction withholds is at stake.
- @moderation @readpath — A ban or timeout binds every write that starts after its response: the
  restriction is read inside the write's transaction, which runs on the primary beside it, so
  replication lag cannot let a write through and the read costs a same-region round trip.
  REJECTED: reading the restriction from a replica — a lagging replica lets a just-restricted user
  write, and closing that with a freshness marker adds machinery to every write for a read that is
  already local to the primary.
  INVARIANT: a write that starts after a ban or timeout response is refused — unenforced until the
  consistency run judges moderation actions.

## Takeaway
- takeaway: a guard that every handler needs must be declared by every handler, including the
  declaration that it needs none; a guard that is merely called can be forgotten without a trace.

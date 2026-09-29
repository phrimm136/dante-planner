# 148 privileged-routes-check-the-live-actor
epic: none · pr: none · supersedes: 022 @ban

## Decisions
- @security @moderation @admin — Every request under the moderation and administration route
  prefixes is authorized against the actor's current database row: a restricted actor is refused
  with its restriction code, and an actor whose stored role no longer reaches the route's role is
  refused as forbidden, before any handler or body validation runs. The token's role claim
  outlives a ban or a demotion by up to its lifetime, and the moderation services load no actor,
  so a banned or demoted moderator kept every takedown, unpublish, hide and comment deletion until
  the token expired; the write-layer enforcement the token design relied on does not exist on
  these routes.
  REJECTED: a handler annotation read by an interceptor — an annotation can be forgotten on a new
  handler, and 057 already rejected annotation-driven checks for their double lookup and
  self-invocation holes.
  REJECTED: an explicit actor check at the top of each moderation service method — the existing gap
  is exactly one such call present in one service and absent in the next.
  REJECTED: a per-request revocation stamp on every route — the cost 022 declined still holds for
  ordinary routes, where the restriction checks follow the planner's public state.
  INVARIANT: a restricted or demoted actor mutates nothing and reads nothing under a privileged
  prefix — the restricted-moderator integration test over every moderation action, and the
  privileged-route coverage rule that confines moderation and administration handlers to those
  prefixes.
  NON-GOAL: ordinary routes keep lazy enforcement; an owner's own unpublish, delete and comment
  deletion stay open to a restricted owner.
- @security @modules — Feature modules contribute request-authorization rules to the chain as
  beans, and the shared chain applies every contribution before its authenticated catch-all; the
  user module contributes the privileged-route rules because it owns role and restriction. The
  shared chain importing a feature module is the edge the module topology removes.
  REJECTED: building the rules inside the shared security configuration — a new dependency from
  shared onto the user module.
  REJECTED: placing them in the moderation module — the administration prefix is not moderation's.
- @security @tokens — A demotion revokes no token: every route that depends on a role reads the
  stored role, so a token's role claim authorizes nothing.
  REJECTED: revoking a demoted user's tokens after commit — a guard for a claim no check reads,
  with a crash window of its own.
  INVARIANT: no code outside the security chain reads the role claim or granted authorities for
  authorization — the role-claim architecture rule.

## Takeaway
- takeaway: a lazy-enforcement argument holds only where the enforcement exists; the privileged
  routes were exactly where no write-layer check ran.

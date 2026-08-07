# 053 single-refresh-rotation-strategy

## Decisions

- @auth @refresh-rotation — Lineage rotation is the sole refresh-token strategy; the
  grace-mode blacklist-on-rotation path in the auth filter and the
  `jwt.rotation.lineage-enabled` flag are deleted. The flag has run true in config and
  deployment since the production flip, leaving the grace path unreachable, and the two
  paths answered the same concurrent-refresh race with two conflicting tunables (5 s
  blacklist grace vs 30 s memoized-successor reuse window).
  REJECTED: keeping the flag as a rollback lever — rollback would revive a path with no
  traffic and no exercised tests, and reintroduce the dual-tunable ambiguity.
  REJECTED: deleting lineage and keeping the grace-mode blacklist — loses theft
  detection (a replayed retired refresh token revoking its family).
- @auth @refresh-rotation — Corollary: the legacy-admission path
  (`jwt.rotation.legacy-admit-enabled`, deterministic family synthesis for pre-lineage
  tokens) is deleted with it. Its stated end condition — all live refresh tokens carry
  lineage claims once the 7-day TTL window after the flip passed — has arrived, and
  config already runs it false.

- @auth @refresh-rotation — Per-token blacklist, user-wide invalidation, and family
  lineage state merge into one service owning all three Redis keyspaces and both Lua
  scripts. The logout script already writes the family keyspace and the rotation script
  already reads the invalidation keyspace, so each atomic protocol spans what were two
  services; single ownership ends the cross-service key-format reach-in.
  REJECTED: a revocation facade over the two services — call sites simplify but the
  format coupling underneath stays.
  REJECTED: a shared key-format module consumed by both services — two owners for one
  atomic protocol, split exactly where the scripts cross.

## Takeaway

- takeaway: a coexistence flag whose removal condition is never written down outlives
  its window silently; state the condition at the flip, and treat a flag that has been
  constant in every environment as a deletion candidate, not configuration.

# 023 concurrency-control-and-race-arbitration
epic: none · pr: none

## Decisions
- @concurrency @constraint (taste) — When a database constraint can own an invariant, prefer the constraint plus a handled violation over pessimistic serialization; reserve pessimistic locks for invariants no constraint can express. REJECTED: locking as the default arbiter — a unique key arbitrates the race in the engine at no coordination cost, while a pessimistic read serializes every caller that takes it, including the overwhelming majority that were never in conflict.
- @dedup @insert-ignore — Deduplicated inserts use an ignoring insert plus an increment conditional on a row having been created, rather than an existence check followed by a save. REJECTED: a select-for-update dedup pair — the composite primary key already enforces the uniqueness the check was re-deriving, and the lock time on those one-row reads matched their query time.
- @oauth @race @arbitration — Identity resolution happens outside any transaction; a miss enters a transactional create-or-recover; the provider unique key referees the race; the loser's transaction dies whole and the caller retries once in a fresh transaction. REJECTED: holding a transaction open across the lookup — it converts a read that almost always hits into a write-path cost on every login.
- @logout @atomicity — Logout revocation is a single atomic script. REJECTED: sequential revocation calls — a crash between them leaves the refresh token outliving the access token it was issued alongside, which is exactly the state revocation exists to prevent.
- @optimistic-lock @409 — An optimistic-lock failure maps to 409 carrying a reason discriminator that separates a stale client from a genuinely concurrent write. REJECTED: a server-side retry — divergence already exists by the time the failure surfaces, so the retry provably converges to the same 409 while hiding which of the two situations occurred.
- @counters @updatable-false — While counter columns still share an entity with owner-mutated fields, they are mapped non-updatable. REJECTED: leaving them writable — a full-column entity update restores a stale in-memory snapshot over a version-blind bulk increment, silently losing counts.

## Takeaway
- takeaway: most contention is contention with a constraint, not with another caller. Asking which invariant is at stake and whether the engine can already express it usually replaces a lock with an error handler, and an error handler only costs the callers that actually collided.

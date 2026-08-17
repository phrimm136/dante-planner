# 048 transactional-effect-placement
epic: none · pr: none

## Decisions
- @effects @observers — Observer effects (SSE publishes, notification fan-out) run in after-commit listeners: an effect that must never announce uncommitted state follows the commit, and a missed run must be recoverable from committed state. Inline publishes fire on every rollback path, while the post-commit crash window is milliseconds wide and recoverable. REJECTED: publishing inside the transaction — subscribers receive events for rows that never committed, on every ordinary conflict rollback.
- @effects @guards — Guard effects (token revocation during account deletion) run inline before the commit, ordered so their failure aborts the transaction; the hard-delete revocation's swallowed failure is licensed solely by the soft-delete revocation having already run. Deferring a guard makes a deleted account with live tokens reachable after a crash, with no durable record to retry from. REJECTED: moving guards after commit for uniformity with observers — the unaffordable failure direction becomes reachable.

## Superseded
- @effects @observers → 072 — REJECTED: a transactional outbox — the notification row already commits transactionally, a missed announcement is recoverable by design, and relay infrastructure breaches the no-new-async-machinery constraint.

## Takeaway
- takeaway: two stores with no shared transaction cannot be atomic, so ordering substitutes — choose which store leads per effect by which stale combination is survivable; observers follow the commit, guards precede it.

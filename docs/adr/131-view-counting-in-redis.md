# 131 view-counting-in-redis
epic: none · pr: none · supersedes: 010 @views @best-effort, 010 @views @buffer @scheduling

## Decisions
- @views @redis — A view is recorded by `POST .../viewcount` (ADR 116) as one Lua script on the auth Redis primary: `SET NX` on a per-day viewer key deduplicates, and only a new key increments a per-planner counter in a buffer hash. Pods hold no view state, so a pod crash or a deploy loses nothing; ADR 010 rejected durable recording because it put a write on the read path, and the split moved recording off that path, and write forwarding (ADR 123) runs it beside the Redis primary.
  REJECTED: the per-pod in-memory buffer — loses up to one flush window on every pod restart, deploys included, and keeps state in pods.
  REJECTED: flushing the buffer on graceful shutdown only — covers deploys, not crashes or OOM kills.
  REJECTED: incrementing MySQL per view — one primary transaction per view, the cost ADR 010 rejected.
- @views @flush — One scheduled job under ShedLock renames the buffer hash to a batch key, applies the increments to MySQL in one transaction that also records the batch id, then deletes the batch key; a batch key left by a crash is replayed, and one whose id is already recorded is deleted without applying. Redis's append-only log syncs every second, so a Redis crash can still lose up to a second of views.
  REJECTED: applying increments without a batch record — a crash between the MySQL commit and the key delete would count the batch twice.

## Takeaway
- takeaway: an in-memory buffer is only cheap until the process that holds it restarts; once recording is off the read path, the durable store costs nothing a reader feels.

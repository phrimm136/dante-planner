# Pre-Seoul Readiness Gates — Recorded Measurements

**Scope:** three empirical gates that must clear before a second (Seoul) region begins serving
against a cross-region MySQL read-replica and a `REPLICAOF` Redis. Each gate protects a specific
production risk introduced by the read-local / write-global topology. This document records the
target, the methodology, and the measured result (or the reason a real number is deferred).

| Gate | What it protects | Status |
|------|------------------|--------|
| **Gate 2 — Write-path round-trip audit** | Cross-region write latency (Seoul → Oregon primary over VPC peering) | **Complete (local).** Every core CRUD write path is bounded; one bulk endpoint flagged for hardening. |
| **Gate 1 — Backend RSS under load** | App-node instance sizing (`t4g.small` 2 GiB vs `t4g.medium`) | **Partial (local).** Target + methodology recorded; real number needs load-test infra. |
| **Gate 3 — `ReplicaLag` p99 baseline** | Tombstone-TTL and GTID-probe margins | **Deferred to post-Phase-14.** Requires the live Seoul replica. |

---

## Why these gates exist — the topology

Seoul pods read from a region-local RDS read-replica and a local Redis replica, but **all writes go
to the Oregon primary** (DB, rotation Lua, tombstones, SSE publish) over VPC peering. The measured
cross-region round-trip is **~130 ms** (a Seoul write transaction is budgeted at ~0.2 s end-to-end in
the pool ledger). The Seoul→primary write pool is deliberately small — **10 connections** (Little's
law: ~5 write-RPS × 0.2 s WAN txn).

Two consequences drive the gates:

- **Every *extra sequential* DB round-trip inside a write transaction costs ~130 ms of WAN latency
  while holding one of only 10 scarce write connections.** A write that fans out to N sequential
  round-trips holds a connection for ~130 ms × N and compounds pool pressure. This is Gate 2.
- **The 2 GiB app node must hold the JVM under load** without the OOM-killer firing, or the sizing
  decision (`t4g.small` vs the 2× costlier `medium`) is wrong. This is Gate 1.

---

## Gate 2 — Write-path round-trip audit

### Target (Done-When)

> Every write transaction is single-round-trip before Seoul exists.

### Operational definition of the criterion

"Single-round-trip" is **not** read literally — a normal load-then-mutate JPA write is a `SELECT`
plus a batched flush, which is fine. The audit classifies each write transaction as:

- **BOUNDED-OK** — a fixed, **data-size-independent** number of round-trips. Load-then-mutate
  (`SELECT` + one flush) and 2–4 sequential round-trips (load entity, load related, atomic update)
  are all fine. These are *not* violations.
- **VIOLATION** — **unbounded per-row fan-out that grows with data size**: an N+1 `SELECT` pattern,
  a lazy-association traversal inside the transaction that triggers a query, or a `save`/`persist`/
  `delete` executed inside a loop (per-row DML). A bounded write can be 200–400 ms; an unbounded one
  is 130 ms × N and scales with user input.

### Method

Enumerated every write transaction in the backend — all `@Transactional` service methods **without**
`readOnly=true` (≈50 methods across 13 services) — and traced each method body plus its private
helpers and repository calls for: (a) lazy getters on `FetchType.LAZY` associations, (b) `save`/
`saveAll`/`delete` inside a `for`/`forEach`/`stream` loop, (c) `REQUIRES_NEW` notification methods
invoked once-per-recipient in a caller loop. The enumerated LAZY associations under watch:
`Planner.user` (`@ManyToOne LAZY`), `User.settings` / `UserSettings.user` (`@OneToOne LAZY`).

### Result — audit table

All ≈50 write transactions are **BOUNDED-OK** except the rows called out below. Representative
coverage by domain (every method was traced; the table lists the load-bearing ones):

| Write transaction (file:line) | Class | Round-trips | Notes |
|---|---|---|---|
| `PlannerCommandService.createPlanner` (:164) | BOUNDED-OK | 2 SELECT + 1 INSERT | single entity |
| `PlannerCommandService.updatePlanner` (:314) | BOUNDED-OK | 2 SELECT + 1 UPDATE (+reindex DELETE + one batched `saveAll` if published) | fixed |
| `PlannerCommandService.deletePlanner` (:352) | BOUNDED-OK | 2 SELECT + optional index DELETE + 1 UPDATE (+ tombstone) | fixed |
| **`PlannerCommandService.importPlanners` (:383)** | **VIOLATION** | 2 SELECT + **N INSERTs** (loop `save` at :423 over `req.planners()`) | grows with request size |
| `PlannerPublishingService.togglePublish` (:49) | BOUNDED-OK **(1 lazy-load flag)** | ~5 fixed + `Planner.user` lazy load at :93–98 (first-publish only) | see flag below |
| **`PlannerIndexService.reindex` (:41)** | **VIOLATION** | 1 DELETE + `saveAll(N entries)` = **N INSERTs** (N = extracted index terms); runs inside every published-planner update/publish | grows with content size, **hot path** |
| `PlannerEngagementService.castVote` (:74) | BOUNDED-OK | ~5 fixed; atomic `x=x+1` UPDATE; recommend-notify is `AFTER_COMMIT` (outside txn) | hot-row, but bounded |
| `PlannerEngagementService.toggleBookmark` (:149) | BOUNDED-OK | 2 SELECT + 1 branch DML | fixed |
| `CommentService.createComment` / `createReply` (:175/:270) | BOUNDED-OK | ≤4 SELECT + 1 INSERT; notify is single-recipient | fixed |
| `CommentService.toggleUpvote` (:397) | BOUNDED-OK | ~5 fixed; atomic increment | hot-row, bounded |
| `ModerationService.*` (14 write methods) | BOUNDED-OK | each 1–3 SELECT + 1 UPDATE + 1 audit INSERT | all single-target; no "ban-all" fan-out |
| `UserAccountLifecycleService.performHardDelete` (:121) | BOUNDED-OK | 5 bulk `@Modifying` queries + 1 `delete(user)`; planners removed by **DB FK cascade**, not per-row JPA | bulk, not looped |
| `NotificationService.notifyPlannerPublished` (:253) | BOUNDED-OK **(scaling watch)** | 1 SELECT (recipient ids) + `saveAll(N notifications)` | `REQUIRES_NEW`, first-publish only — see watch note |
| `NotificationService.notifyPlannerRecommended/CommentReceived/ReplyReceived` (:135/:169/:215) | BOUNDED-OK | 1 INSERT each; each caller invokes **once**, never in a loop | isolated `REQUIRES_NEW` |
| `UserService`, `UserSettingsService`, `AdminService` write methods | BOUNDED-OK | 1–3 SELECT + 1 UPDATE/INSERT; bounded retry loop on username collision (data-independent) | fixed |

### Finding 1 (headline) — VIOLATION: `reindex` re-writes N index rows on every published edit

`PlannerIndexService.reindex` (`:41`) is called **inside the write transaction** of `updatePlanner`,
`upsertPlanner`, and `togglePublish` — i.e. on **every update to a published planner**, not a rare
endpoint. It deletes the planner's content-index rows and re-inserts them: `deleteByPlannerId` (1 DELETE,
`:43`) then `contentIndexRepository.saveAll(entries)` (`:67`), where `entries` is **one row per extracted
content term** (identities, E.G.O.s, E.G.O. gifts, theme packs). Round-trip count = **1 DELETE + N
INSERTs**, and **N grows with planner content size**.

- `saveAll` is *not* one round-trip: it `persist()`s each element and flushes. `PlannerContentIndex`
  implements `Persistable` with a transient `isNew=true` flag (`:39,:58`), so each is a clean `persist`
  (no per-row pre-`SELECT`) — but with **no `batch_size` configured**, the flush emits **N individual
  INSERT statements**.
- The id is an **assigned composite natural key** (`@IdClass`, no `@GeneratedValue`) → **batchable**. So
  `hibernate.jdbc.batch_size` + `order_inserts` would collapse the N INSERTs into a few round-trips.
- **WAN cost:** a routine edit-and-save of a published planner with N index terms = **~130 ms × N** on the
  size-10 Seoul write pool. Because this is the common publish/update path (not a rare bulk import), it is
  operationally the **most important** fan-out found.

**Remediation:** same lever as Finding 2 — enable `hibernate.jdbc.batch_size` (+ `order_inserts=true`);
the assigned composite key makes these INSERTs batchable.

### Finding 2 — VIOLATION: `importPlanners` bulk insert is unbatched per-row fan-out

`PlannerCommandService.importPlanners` (`:383`) validates a user's plan cap once, then loops over the
request's planner array (`for (… : req.planners())`, `:397`) calling `plannerRepository.save(planner)`
**per element** (`:423`). Round-trip count = **2 fixed SELECTs + N INSERTs**, where N is the number of
planners in the import request — it **grows with user input**.

Precise mechanics (why this is genuinely N round-trips, and why it is fixable):

- `Planner` has a nullable `@Version Long version` (`:147`) left unset by the import builder, so Spring
  Data's `isNew()` returns `true` → `save()` calls `em.persist()` (not `merge()`). Good: there is **no**
  extra pre-`SELECT` per row. The cost is purely the N INSERTs flushed at commit.
- `Planner`'s id is an **application-assigned `UUID`** (`.id(UUID.randomUUID())`, `:411`) — *not*
  `GenerationType.IDENTITY`. Assigned ids **are batchable** by Hibernate. But the project configures
  **no** `hibernate.jdbc.batch_size` anywhere (confirmed: no `batch_size` / `order_inserts` /
  `order_updates` / `batch_versioned_data` in any `.properties` file, no `application.yml`, no
  programmatic `HibernatePropertiesCustomizer`), so the flush emits **N individual INSERT statements**.
- **WAN cost:** over the Seoul→Oregon path, a bulk import of N planners = **~130 ms × N** holding one of
  the 10 Seoul write connections for the whole loop.

**Remediation (pre-Seoul hardening candidate):** add
`spring.jpa.properties.hibernate.jdbc.batch_size` (+ `order_inserts=true`) and let the assigned-UUID
INSERTs collapse into a small fixed number of batched round-trips — the same single config change fixes
Finding 1.

### Finding 3 — FLAG (bounded, not a blocker): `togglePublish` lazy-loads the author

`PlannerPublishingService.togglePublish` loads the planner via `findById` (`:54`, no fetch join), then on
the **first-publish** branch reads `saved.getUser()` and `author.getUsernameEpithet()` /
`getUsernameSuffix()` (`:93–98`) — a getter on the `Planner.user` `@ManyToOne LAZY` association that
triggers one extra `SELECT` inside the write transaction. This is a **single, constant** round-trip
(not size-dependent), so it is not a violation. Optional optimization: `JOIN FETCH p.user` on the load
at `:54`. (Note: `isOwnedBy(...)` elsewhere calls `user.getId()`, which reads the FK off the proxy and
does **not** trigger a query — only the epithet/suffix access does.)

### Finding 4 — WATCH (write-scaling ladder): the publish broadcast

`NotificationService.notifyPlannerPublished` persists one notification per opted-in recipient via a
single `saveAll`. Because `Notification` uses `GenerationType.IDENTITY`, Hibernate **cannot** batch these
inserts regardless of `batch_size` — it is N INSERTs by construction. Mitigating factors: it runs in an
isolated `REQUIRES_NEW` transaction, fires **only on a planner's first publish**, and the recipient set is
bounded by users who enabled `notifyNewPublications` (**default `false`**). This is not a core-CRUD
single-round-trip blocker; it belongs on the **write-scaling ladder** (the same class of concern as the
hot-row vote counters) and is recorded as a watch item rather than a Gate-2 failure.

### Gate 2 verdict

**Audit complete and recorded.** All single-entity write paths (create/update/delete planner, vote,
bookmark, comment, moderation, user/settings/admin, per-notification writes) are **bounded /
single-round-trip**. The Done-When target "every write transaction single-round-trip" is met for those
paths with **two content-indexing/import fan-outs carried forward** — `reindex` (Finding 1, the hot
publish/update path) and `importPlanners` (Finding 2, bulk import). Both are **N unbatched INSERTs** with
assigned (batchable) keys, and **both are fixed by the same single config change**: enabling
`hibernate.jdbc.batch_size` + `order_inserts` (absent today). That missing config is harmless for the
single-entity paths but is the direct lever for both violations.

---

## Gate 1 — Backend RSS under load test (PARTIAL — local)

### Target (Done-When)

> Backend RSS under `load-test-*.js` must fit **≤ ~1.3 GiB** on the 2 GiB app node.
> Pass → app nodes are `t4g.small` (2 GiB); fail → `t4g.medium`.

### Measurement methodology

- **Load generator:** k6 against `scripts/load-test.js` (driver) + `scripts/lib/load-test-shared.js`
  (endpoint mix). Its stage profile ramps VUs 10 → 50 → 200 → 500 → 1000 to find the pool-saturation
  and memory ceiling; companions `load-test-sustained.js` (soak) and `load-test-sse.js` (streaming) probe
  the sustained and connection-heavy regimes.
- **Backend under test:** brought up with the `loadtest` Spring profile via
  `docker-compose.loadtest.yml` (activates `dev,loadtest`, exposes `:8080` actuator, removes rate limits
  so throughput reflects server capacity). **Not executed here** — the brief scopes this gate to
  recording the plan, not standing up the load-test stack.
- **RSS capture:** container process RSS via
  `docker stats --format "… {{.MemUsage}}" backend` (documented in `application-loadtest.properties` and
  the compose header), read continuously through the ramp; the peak steady-state RSS is the gate number.
  In production the equivalent signal is the CloudWatch **Per-Process Memory (RSS)** panel
  (`procstat_memory_rss`) on the ops dashboard.

### What bounds the number today (JVM heap config)

The container caps the JVM at **`-Xms256m -Xmx768m -XX:MaxMetaspaceSize=256m`**
(`backend/Dockerfile:41`, `docker-compose.yml`). JVM-managed memory is therefore bounded at roughly
**768 MiB heap + 256 MiB metaspace ≈ 1.0 GiB**, leaving ~300 MiB of headroom under the 1.3 GiB target for
thread stacks, code cache, and direct/native buffers. On paper the target holds comfortably.

### Why this stays "partial" until a real run

The ops alarm set explicitly watches for **"traffic-correlated JVM off-heap growth"** (the
`mem_available < 200 MiB` sustained alarm cross-references the Per-Process RSS + JVM Heap panels). Off-heap
RSS — Netty/NIO direct buffers, Lettuce connection buffers, GTID/SSE working set under concurrency — is
exactly the component the heap math does **not** bound, and only a real load run resolves it. **Recorded
as partial-local: a real RSS number requires executing the load-test stack.**

---

## Gate 3 — `ReplicaLag` p99 baseline (DEFERRED — post-Phase-14)

### Target (Done-When)

> Document the `ReplicaLag` p99 once the Seoul replica runs — the empirical justification for the
> tombstone TTL and the GTID-probe (`WAIT_FOR_EXECUTED_GTID_SET(gtid, 0.05)`) margins.

### Status

**Deferred to after Phase 14 (Seoul region + cross-region RDS read-replica over VPC peering).** There is
no live cross-region replica in the local Testcontainers harness — the harness proves the *mechanism*
(pause/resume replication, `awaitCaughtUp`) but cannot produce a production lag distribution. This gate is
a **carried-forward, post-Phase-14 measurement**; the causal-consistency stack it justifies (GTID cookie
gate, primary re-check, Redis tombstones) is designed to be *correct under arbitrary lag*, so a real p99 is
needed only to right-size the cleanup-TTL / probe-margin knobs, not to validate correctness.

### Intended methodology (recorded now)

Read RDS Enhanced Monitoring / CloudWatch **`ReplicaLag`** on the Seoul read-replica over a representative
window (include a peak-traffic period), report p50/p95/**p99** and the max spike. Cross-check against the
Redis replication **offset delta** (both are on the alert set). Use the p99 to justify: the 1 h content-
tombstone TTL (cleanup horizon ≫ lag) and confirm the 50 ms GTID probe bound stays a *probe*, not a lag
window (longer lag simply keeps routing the author to primary).

---

## Summary

- **Gate 2:** audit complete; single-entity write paths bounded; **two violations** carried forward as a
  pre-Seoul hardening item — `reindex` (N unbatched index INSERTs on every published edit, the hot path)
  and `importPlanners` (N unbatched INSERTs on bulk import). Both have assigned/batchable keys and are
  fixed by the **same single config** (`hibernate.jdbc.batch_size` + `order_inserts`), which is absent today.
- **Gate 1:** target ≤ 1.3 GiB; methodology and heap-bound (~1.0 GiB JVM-managed) recorded; **partial —**
  a real RSS number needs a load-test run because off-heap growth is unbounded by config.
- **Gate 3:** deferred to post-Phase-14; intended `ReplicaLag` p99 methodology recorded.

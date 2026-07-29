# Architecture Verification Plan — real traffic

Purpose: prove each architectural claim under **real production traffic**, not synthetic tests or
static validation. The repo's own lesson governs this plan: *a failover never observed failing over
is a comment*, and a recovery documented-but-never-exercised (the Flyway case) is false confidence.
Every row below ends in a **measured number**, tied to an **existing alert-set metric**, producing a
**portfolio evidence artifact**.

## 1. Traffic model — the safety rules that shape everything

Three real-traffic modes; one hard correctness rule splits them.

- **Mirror (shadow) — READS ONLY.** A tee duplicates live requests to the cluster while prod still
  serves. **Every stack writes to the same RDS**, so a mirrored `POST`/`DELETE` executes *twice* —
  duplicate planners, double blacklist entries, doubled side effects. Mirror is therefore restricted
  to idempotent GET paths. **Mechanism reality:** Cloudflare has no native request-mirroring; this
  needs a **Cloudflare Worker that tees GETs** to the cluster origin (fire-and-forget, response
  discarded) or a Traefik-level shadow service. Pick the Worker tee — it sits where our entry plane
  already is. *If neither is stood up, the mirror rung is hypothetical and those claims fall back to
  canary.*
- **Canary — READS + WRITES.** Cloudflare weighted routing sends a real **5 → 25 → 100%** slice to
  the cluster (single execution, so writes are safe). Shared RDS makes rollback a **one-DNS-edit**
  with no data repair. This is the only safe channel for every write-path claim.
- **Fault injection under load** — region-kill, pod-kill, Redis/RDS outage **while real traffic
  flows**. Ordered per §4 so users are never exposed to an unproven failure.

### Controlled-burst exceptions (organic traffic can't drive these — user ruling requested)
The scope excluded synthetic load. Three claims cannot be verified by organic traffic and need a
**deterministic controlled burst** (a bounded k6/`hey` run, not open-ended load); everything else
stays on mirror+canary:
1. **Rate limit / bucket4j (INV7)** — proving the threshold *trips* needs a deterministic request
   count at a known rate; organic traffic won't reliably hit the limit.
2. **Backend RSS ≤ ~1.3 GiB gate** — load-to-failure on the 2 GiB node; you cannot push *real users*
   to the breaking point.
3. **ReplicaLag p99 baseline (Gate 3)** — needs controlled write volume to characterize the window.

## 2. Phasing — half of this is executable on Oregon *today*

Bucket by whether the claim needs a second region / live replica. This makes ~half the plan runnable
now instead of blocked on the full Seoul spend.

- **Verifiable on Oregon today:** INV6 fail-open, per-region rate limit (INV7), app-node cattle-kill,
  blacklist-on-logout rejection, SSE fan-out (`desired=2`), backend RSS gate, Multi-AZ same-site
  failover.
- **Needs Seoul (or a temporary Oregon read replica):** INV1/INV2 absence+tombstone, INV3 GTID cookie
  gate replica-vs-primary routing, read-local/write-global, GA ~30s failover, Traefik connection-fail
  fallback, REPLICAOF freshness, client-IP preservation, cross-region no-autonomous-promotion.
  > **Cost lever:** a *temporary same-region* RDS read replica in Oregon lets you verify the causal
  > claims (INV1/2/3, read-local) under real traffic **before** committing the Seoul two-region spend.
  > Recommended: run the "needs-replica" causal claims against a throwaway Oregon replica first.

## 3. Verification matrix

### 3a. Oregon-today (run before the Seoul spend)

| Claim | Traffic mode | Pass number | Observe (existing metric) | Rollback |
|---|---|---|---|---|
| INV6 fail-open | Fault: kill auth Redis, under **canary** | Authed reads keep 200; blacklisted token still rejected after AOF replay | `blacklist_check_skipped_total` increments, then returns to 0 | Restart redis-auth StatefulSet |
| Rate limit INV7 | **Controlled burst** | 429 at the configured threshold, per-region buckets independent | bucket4j 429 rate | Burst ends |
| Bulkhead INV7 | Controlled burst: 130 ms toxic + junk-UUID flood | Concurrent legit-write SLA holds | write-path p99 | Stop flood |
| App-node cattle-kill | Fault: terminate an app node, under **canary** | ASG replaces, pod Ready ~14 s (Phase-13 proven), zero 5xx to canary | pod Ready stopwatch; canary 5xx flat | ASG self-heals |
| Blacklist-on-logout | **Canary (write)** | Logout → re-present token → **rejected** | `jwt_rotation_outcome_total` | N/A (read-only assertion) |
| SSE fan-out | **Canary + mirror**, `desired=2` | Event delivered to subscribers on **all** pods (not single-pod) | SSE delivery count vs pod count | Revert `desired=1` |
| Backend RSS gate | **Controlled burst** (load-to-failure) | RSS ≤ ~1.3 GiB on the 2 GiB node | JVM memory vs limit | Burst ends |
| Multi-AZ failover | Fault: force RDS failover, under **canary** | ~60 s automatic writer failover, **RPO 0** | RDS event + write-error window | Managed by RDS |

### 3b. Needs Seoul (or temporary Oregon replica)

| Claim | Traffic mode | Pass number | Observe (existing metric) | Rollback |
|---|---|---|---|---|
| INV1 absence authority | **Mirror (read)** under replication lag | Replica byId miss → 200 (primary re-check), never a bare 404 | `replica_miss_promoted_total` | Read-only; none |
| INV2 tombstone | **Canary** delete → **mirror** read | Deleted entity 404s on replica even while the row lingers | tombstone hit | DNS rollback |
| INV3 GTID cookie gate | **Canary (write)** then gated read | Caught-up → served from replica; not → routed to primary; no timing constant | replica-vs-primary route counter | DNS rollback |
| Read-local / write-global | **Mirror (read)** + **canary (write)** | Reads served from local replica; writes single-round-trip ~130 ms to Oregon | ReplicaLag; write round-trip p99 | DNS rollback |
| GA ~30 s regional failover | **Fault: region-kill** under canary | Traffic re-steers to healthy region in **~30 s** (stopwatch) | GA health flip; `/healthz-local` | GA auto-restores |
| Traefik connection-fail fallback | **Fault: kill local Spring** under canary | Fallback fires **only** on connection failure, never latency/5xx | sustained Traefik `fallback > 0` alert | Local Spring restarts |
| REPLICAOF freshness | **Canary write** (Oregon) → Seoul-local read | Visible after replication; write to Seoul-local **refused** (read-only) | Redis offset delta | N/A |
| Client-IP preservation | **Canary** | Real client IP in Spring logs (not GA's) | access-log source IP | N/A |
| ReplicaLag p99 baseline | **Controlled burst** | p99 window recorded (Gate 3 closes) | RDS `ReplicaLag` | Burst ends |
| No autonomous cross-region promotion | **Fault: kill Oregon primary** under canary | **Nothing auto-promotes** — writes surface typed errors; human runbook required | write typed-error rate; *absence* of a promotion event | Human promote runbook |
| Unattended Seoul bootstrap | Provisioning **is** the test | Hands-free convergence, no manual step | ArgoCD Synced/Healthy | `terraform destroy` |

## 4. Drill protocol (every fault-injection row follows this)

1. **Baseline** — capture steady state with traffic green (latency histogram, error rate, the row's
   metric). This is the "before" portfolio shot.
2. **Inject under read traffic first** — mirror/GET only, **zero user impact**. Confirm the mechanism
   behaves.
3. **Canary at 5%** — repeat the injection with a real 5% slice and **instant DNS rollback armed**.
   Never region-kill at full real traffic.
4. **Measure recovery** — stopwatch the pass number (failover seconds, Ready time, fail-open window).
5. **Confirm return to baseline** — metric back to green; no lingering fallback, no stuck 5xx.

## 5. The negative claim needs special handling

*No autonomous cross-region promotion* is verified by proving an **absence**: kill the primary and
stopwatch that **nothing** auto-promotes — writes return typed errors, and the human runbook is the
only path forward. A drill that makes something *happen* would disprove the claim; success is the
absence holding under load. (Multi-AZ same-site auto-failover is the *allowed* mundane case — bought,
synchronous, same-site — and is verified separately in §3a as a positive claim.)

## 6. Portfolio evidence (the deliverable of each run)

Each drill emits, into `docs/portfolio/`:
- **Before/after latency histograms** (baseline vs under-fault vs recovered).
- **The stopwatch number** on the drill's dashboard panel (GA failover ~30 s, ASG Ready ~14 s,
  Multi-AZ ~60 s, fail-open window).
- **The alert firing and clearing** (screenshot of sustained-fallback / `blacklist_check_skipped` /
  `replica_miss_promoted` going red then green) — proof the alert set is wired, not decorative.
- **A one-paragraph narrative** per claim: what was injected, what held, what the number was.

No new instrumentation: every evidence shot comes from the **existing alert-set metrics** (§ requirements
Observability) — if a claim has no existing metric, that gap is itself a finding.

## 7. Order of execution

1. Stand up the mirror Worker tee + canary weighted routing (entry-plane prerequisites).
2. Run **all of §3a** on live Oregon — before spending on Seoul.
3. Stand up a temporary Oregon read replica; run the causal subset of §3b (INV1/2/3, read-local).
4. Provision Seoul; run the remaining §3b (GA, Traefik, REPLICAOF, cross-region promotion).
5. Collect §6 evidence into `docs/portfolio/` as each drill passes.

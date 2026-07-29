# Phase 15 notes — drills, reference-architecture deviations, write-scaling ladder

The closing infra phase. Phase 15 produces no code and no local tests — it is the
**operational proof** that the two-region fleet behaves as designed under failure, plus the two
reference tables the design owes its reader. Read alongside `plan.md` (Phase 15), `requirements.md`
(Data layer, Observability & ops, Done-When) and `entry-plane.md` (region-kill path).

**Where evidence lives.** Observed drill numbers (dates, stopwatch readings, screenshots) are
recorded in the **out-of-repo portfolio**, not here — "a failover never observed failing over is a
comment" (`requirements.md`). This page is the versioned *procedure, expected timing, and
pass criteria*; the portfolio is the *what-actually-happened*. Each drill below ends with the exact
cells the portfolio run must fill.

---

## 1. Drills

Four drills. Three are named in the Done-When (RDS promote rehearsal, Redis outage, app-node kill);
the region-kill / GA failover is the entry-plane proof (`entry-plane.md` step 4) that closes the
Seoul cutover. Every drill re-verifies its slice of the **alert set** live — an alert that never
fired in anger is unproven.

Alert set under test (`requirements.md` Observability): sustained Traefik `fallback` > 0, RDS
`ReplicaLag` + Redis offset delta, `CPUCreditBalance`, JVM memory vs limit,
`jwt_rotation_outcome_total{theft_revoked}` spike, cert expiry (incl. origin-pull client cert),
`blacklist_check_skipped_total`, `replica_miss_promoted_total`, billing ~$200.

### Drill A — RDS promote rehearsal (break-glass authority change)

**Proves:** the human-executed geographic promotion runbook works, and that the Seoul replica
already carries the primary's posture so a promote yields a legal writer. No autonomous writer
promotion exists anywhere (`mechanics.md §0` FORBIDDEN) — this is the deliberate, rehearsed act.

**Preconditions**
- Rehearse against a **throwaway replica**, never the live Seoul replica.
- **Pre-enable Multi-AZ on the throwaway before promoting** (a promote off a single-AZ replica
  leaves the new primary without a standby — the promote must land already-durable).
- Confirm the replica's region-local parameter group parity: `gtid_mode=ON`,
  `enforce_gtid_consistency=ON`, `require_secure_transport=1` (evidence: prod param group hardened
  in `27ab8d8c`/`1dbb1df9` during 030). Without parity the promoted instance rejects the primary's
  GTID posture.

**Procedure**
1. Create a throwaway cross-region read replica of the primary (or clone the Seoul replica's
   config).
2. Enable Multi-AZ on it; wait for the standby to finish provisioning (`Modifying` → `Available`).
3. Confirm `ReplicaLag` ≈ 0 at the moment of promote — the runbook promotes on a caught-up replica,
   not a lagging one.
4. Promote (`aws rds promote-read-replica`), stopwatch from the API call to `Available` +
   writable.
5. Point a scratch client at the new endpoint; issue one write; confirm it commits.
6. Tear the throwaway down.

**Expected:** promote completes with the new primary already Multi-AZ; a write succeeds; the
`gtid_mode=ON` posture survives the promote.

**Portfolio cells to fill:** promote wall-clock; `ReplicaLag` at promote; write-confirm result;
any parameter-group drift observed.

### Drill B — Redis outage (fail-open + AOF-replay integrity)

**Proves:** the production analogue of **INV6**. Auth Redis is self-hosted (StatefulSet on the data
node, AOF everysec + RDB preamble, EBS gp3, AZ-pinned) — there is no Sentinel and no auto-promote.
An outage must degrade by operation, not fail the request, and recovery must not silently drop a
pre-outage revocation.

**Procedure**
1. Steady state: an authed read works; capture `blacklist_check_skipped_total` baseline.
2. **Blacklist a token** (e.g. force a logout / theft-revoke) so a known-bad token exists in Redis
   before the outage.
3. Kill the auth-Redis pod (or Toxiproxy-cut the data node's `:6379`).
4. Observe: authed **reads still serve** (fail-open); `blacklist_check_skipped_total` climbs and
   the alert fires; login/logout/rotation surface `AUTH_TEMPORARILY_UNAVAILABLE` (typed 503), not a
   500.
5. Let the StatefulSet auto-recover; on restart Redis replays AOF.
6. **Post-replay assertion:** the token blacklisted in step 2 is **still rejected** — the revocation
   survived the outage via AOF. This is the integrity gate.

**Expected:** reads never 500 during the cut; the skipped-check alert fires and clears; the
pre-outage blacklisted token is rejected after AOF replay. Fail-open blast radius is bounded by the
15-min access-token life.

**Portfolio cells to fill:** read-availability during cut; alert fire/clear timestamps; recovery
duration; post-replay blacklist-reject result (PASS/FAIL).

### Drill C — App-node kill (stopwatch the ASG loop)

**Proves:** app nodes are cattle. Spring runs as a DaemonSet on the `role=app` ASG
(min=1/desired=1/max=2); killing one must self-heal without hand-holding. Phase 13 already observed
**ASG replacement Ready ~14s** on the app-node kill — this drill re-confirms it on the current
fleet and ties it to the entry-plane.

**Procedure**
1. Confirm ≥1 app node `Ready`, DaemonSet pod `Running`, `/healthz-local` → 200.
2. Terminate the app EC2 instance (console or `aws ec2 terminate-instances`).
3. **Stopwatch** the ASG loop: termination → new instance → SSM/user-data k3s join → node `Ready` →
   DaemonSet pod `Running` → `/healthz-local` 200 again.
4. Confirm ArgoCD stayed `Synced/Healthy` throughout (the app is GitOps-managed; no `kubectl` drift).

**Expected:** ASG replaces the node and the DaemonSet reschedules hands-free, on the order of the
Phase 13 ~14s baseline. No manual intervention.

**Portfolio cells to fill:** kill→Ready stopwatch; kill→`/healthz-local` 200 stopwatch; ArgoCD
status throughout.

### Drill D — Region-kill / Global Accelerator failover

**Proves:** the entry-plane's ~30s regional failover (`entry-plane.md`). GA health-checks
`/healthz-local` (local Spring readiness only; the Traefik `fallback` route is **excluded**, so a
region limping along on cross-region fallback reads *unhealthy* and GA steers clients to the healthy
region directly). Traefik's cross-region `failover` service trips on **connection failure only**,
never latency/5xx (cascade prevention).

**Procedure**
1. Steady state: both regions healthy in GA; clients proximity-routed. Note which region serves a
   test client.
2. **Kill a region's ingress** (stop the ingress EC2, or block GA-health + client ranges at the SG)
   so `/healthz-local` starts failing there.
3. **Stopwatch** GA: from the health check flipping unhealthy to clients being served by the other
   region.
4. Confirm the surviving region serves **directly** (not via Traefik fallback) — the fallback path
   must read unhealthy to GA by design.
5. Confirm the **sustained Traefik `fallback` > 0** alert behaves as specified (fires only if a
   region is genuinely serving cross-region).
6. Restore the region; confirm GA re-adds it and proximity routing returns.

**Expected:** GA regional failover ~30s; surviving region serves directly; no cascade; fallback
alert semantics correct.

**Portfolio cells to fill:** health-flip→client-served stopwatch; direct-vs-fallback confirmation;
fallback-alert behavior; recovery time.

---

## 2. Deviation table (from the reference-architecture roast)

Where this build knowingly departs from the textbook multi-region reference architecture, and why
the deviation is a deliberate trade rather than an oversight. Each row states the residual risk it
accepts — a deviation with no named residual is hiding one.

| # | Deviation (what we do) | Reference architecture (what the textbook does) | Why we deviate | Residual risk (accepted) |
|---|------------------------|--------------------------------------------------|----------------|--------------------------|
| 1 | Self-managed **k3s** on EC2 ASGs | Managed **EKS** control plane | Cost + a real learning vehicle for the fleet primitives | No cloud-controller-manager → orphaned `NotReady` nodes are **not GC'd automatically**; manual reconcile (Phase 13 open follow-up) |
| 2 | **Instance-profile** IAM (node-scoped) + ESO | Per-pod **IRSA** roles | No EKS → no OIDC provider for IRSA; instance profile is the only boundary | Coarser blast radius: all pods on a node share the node role — documented no-IRSA deviation |
| 3 | **Self-hosted Redis** StatefulSet (AOF+RDB, AZ-pinned) | Managed **ElastiCache** (Global Datastore) | StatefulSet/PVC learning vehicle + cost | The operator owns failover/patching/backup by hand; single-AZ pin |
| 4 | **No Redis Sentinel / no auto-promote** — typed 503 + AOF recovery | Sentinel quorum or ElastiCache auto-failover | Below the scale that justifies quorum machinery | Outage = wait-for-recovery, not seconds-failover; bounded by 15-min token life (Drill B) |
| 5 | **No autonomous RDS writer promotion** — human runbook | Multi-region auto-failover / Aurora Global writer election | Every cross-region authority change must be a deliberate act; quorum unjustified here | Geographic loss needs a rehearsed human promote (Drill A); Multi-AZ covers the same-site case (~60s, RPO 0) |
| 6 | **ArgoCD core per cluster** (hub-spoke rejected) | Central hub ArgoCD managing spokes | Seoul must deploy even if Oregon is dead — no cross-region deploy dependency | Duplicate ArgoCD footprint per region |
| 7 | **CI tag-bump GitOps** (deploy = a commit) | `argocd-image-updater` auto-detect | Simplicity; the deploy audit trail is git | Tag bump rides CI, not a controller (image-updater deferred) |
| 8 | **Public subnets only, no NAT**; SGs are the boundary | Private subnets + NAT gateways | NAT cost avoided; SG allowlist is the real gate | Instances hold public IPs (SG-gated to Cloudflare + GA-health ranges only) |
| 9 | **Cloudflare mTLS Authenticated Origin Pulls** as the real origin gate | WAF + private ALB / NLB | Single edge entry; the origin-pull client cert is the gate, SG is defense-in-depth | A silently expired origin-pull cert 5xxs every request → **cert-expiry alert is mandatory** |
| 10 | **Prometheus per region** (local scrape) + Grafana Cloud two datasources | Central Prometheus / managed observability | Grafana Cloud survives Oregon's death; local scrape has no cross-region dependency | Two datasources to reconcile; per-region retention |
| 11 | **Causal correctness with no timing constants** (GTID cookie gate, tombstones, primary re-check) | Read-after-write via a fixed pin/delay window | Timing constants are environment-calibrated assumptions that rot silently; causality is checkable | More moving parts (cookie + gate + re-check + tombstone) — each partitions a distinct slice (INV1–3) |

---

## 3. Write-scaling ladder

Write scaling is **explicitly deferred** (`requirements.md`): read replicas amplify reads and never
absorb writes, so the two-region read-local/write-global posture buys nothing for write throughput.
When writes become the ceiling, climb this ladder in order — each rung is cheaper and less invasive
than the next, and you only take a rung when the one below it is genuinely exhausted.

**Rung 0 — current posture.** One MySQL primary in Oregon takes every write (DB, rotation Lua,
tombstones, SSE publish); Seoul writes reach it over peering (~130ms, single round-trip enforced by
the pre-Seoul N+1 audit). Replicas are read-only. This is the baseline the ladder climbs from.

**Rung 1 — Write less.** Eliminate writes before scaling anything that serves them. Debounce/batch
planner autosave, collapse chatty write paths, drop writes the product doesn't need. Zero new
infrastructure; it just raises the ceiling of every rung above. Trigger: write QPS rising but
dominated by avoidable or batchable writes.

**Rung 2 — Scale up (vertical).** Move the primary to a larger instance class (micro → small → …).
The constraint on this workload is the **buffer pool, not connections** (`requirements.md` — check
micro→small before cutover). Simplest lever, no code change, RPO/RTO unchanged. Trigger: sustained
buffer-pool pressure / write latency climbing with writes that are already necessary and batched.
Ceiling: the largest single instance you're willing to pay for.

**Rung 3 — Hot-counter Redis offload — the first realistic escalation.** The write hotspot is not
spread evenly; it concentrates on a few **hot rows**. For this app that is **vote counters**
(planner/comment votes): a single row taking a storm of `UPDATE … SET count = count + 1`. Move the
increment to a **Redis atomic counter** (`INCR`, already the state-holder platform) and flush the
aggregate back to MySQL periodically/asynchronously. This removes the hottest write rows from the
primary without touching the rest of the schema — named in `requirements.md` as the first realistic
escalation. Trigger: a small set of rows dominates primary write load and vertical scaling is spent.
Cost: durability/consistency trade on the counter (Redis AOF window; eventual flush) and a
reconciliation path.

**Rung 4 — Shard (last resort).** Partition the data across independent primaries (by tenant /
entity). Only past this point does the write ceiling actually move without bound — but it imports
cross-shard transactions, rebalancing, and routing complexity that dwarfs every rung above. Take it
only when scale-up **and** hot-row offload are both exhausted.

**The through-line:** replicas never help writes; the cheap rungs (write-less, scale-up) buy time; a
**targeted** hot-row offload defers sharding for a long time because real write load is rarely
uniform. Sharding is the tool of last resort, not the first reflex.

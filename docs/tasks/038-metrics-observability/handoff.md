# Task 038 — Metrics & Observability: session handoff

Design was settled in the 2026-07-13 session (the multi-region cutover / read-local
incident day). **Nothing below is implemented yet** except where marked. This document
is the single source for the implementing session: selection, design constraints,
and the incident context that motivated each choice.

## Why now (incident context)

Two postmortems from 2026-07-13 drive the priorities:

- `~/postmortems/2026-07-13-seoul-read-local-unwired.md` — Seoul served all reads
  cross-Pacific for 2h25m; the Seoul RDS replica sat at **0 connections** the whole
  time and nothing alerted. Detection was the operator's browser.
- `~/postmortems/2026-07-13-deploy-window-outage.md` — deploy-window 503s and a
  scale-in black hole; both were caught by ad-hoc curl probes, not monitoring. The
  ~30s hang mechanism under burst is STILL unresolved (Traefik dialTimeout suspected;
  Traefik access logs are the prerequisite to that investigation).

Guiding principle chosen for this task: **select metrics by working backwards from
incidents this system has actually had** (silent zeros, pool saturation, replica lag,
burstable-credit exhaustion), not by enabling textbook catalogs.

## Current state (verified 2026-07-13)

- Per-region Prometheus pods run in-cluster (`danteplanner` ns) and scrape the backend
  via `prometheus.io/*` annotations on the Spring DaemonSet (`/actuator/prometheus`).
- CloudWatch: RDS alarms (CPU / FreeableMemory / DatabaseConnections / ReplicaLag),
  backup dead-man's-switch heartbeat, JVM+MySQL metrics **double-published to CW**
  (to be retired). Sentry for exceptions.
- Grafana Cloud is the intended sink (034 spec, Observability section) — not wired.
- NOT deployed: redis_exporter, node_exporter, blackbox exporter, Traefik metrics
  (`--metrics.prometheus`), Traefik access logs, any tracing.
- Domain counters already exposed by the app: `replica_miss_promoted_total`,
  `blacklist_check_skipped_total`, `jwt_rotation_outcome_total`.
- Deploys are GitOps on the **dev** branch (ArgoCD both regions; main promotion is a
  planned later cutover). Anything you deploy goes through `deploy/base` +
  `deploy/overlays/{oregon,seoul}` and rolls via the surge pipeline automatically.

## Selected metrics (final)

### A. CloudWatch migration
- RDS metrics/alarms: consume via **Grafana Cloud CloudWatch datasource** (no exporter).
- Retire the app's JVM/MySQL CW publishing (Prometheus already has it).
- Generalize the dead-man pattern to Prometheus `absent()` alerts.

### B. HTTP / app (micrometer config only)
- `http_server_requests` percentile **histograms** (p50/95/99 by uri/method/status):
  `management.metrics.distribution.percentiles-histogram.http.server.requests=true`.
- HikariCP per-pool metrics with the **three pools distinguishable**
  (primary/replica/bulkhead): active, idle, **pending**, acquire-time p99, timeouts.
  Sustained `pending > 0` is the early signal of the incident-day pool saturation.
- Keep existing domain counters; ADD: GTID gate outcome counter (replica-hit vs
  primary-fallback — user-perceived replication-lag proxy), SSE active-connections
  gauge, cross-region write latency histogram.

### C. JVM
- GC pause histogram (by cause), allocation rate, promotion rate, metaspace, thread
  states.
- **Niche pick (user-selected): per-class heap occupancy.** Mechanism A = sidecar/cron
  running `jcmd GC.class_histogram` every ~5min, exporting **top-30 classes + `_other`**
  as gauges (fixed label set — class names are unbounded cardinality; merge
  `$$Lambda`/proxy classes). Mechanism B = JFR `ObjectAllocationSample` on demand for
  allocation attribution. Rationale: a known past memory-leak concern; census (A) finds
  the leaking class, JFR (B) finds who allocates it. Safepoint cost of A on a ~768MB
  heap is tens of ms — acceptable at 5min cadence.

### D. Redis (tokens moved here; load will follow)
- redis_exporter (both auth and ratelimit instances; Seoul replica too): used_memory
  family, `mem_fragmentation_ratio`, keyspace hit ratio, connected/blocked clients,
  **replication offset delta** (Seoul REPLICAOF lag in bytes).
- `MEMORY STATS` split: dataset vs overhead vs **client output buffers vs repl
  backlog** (load often lands in buffers, not dataset).
- **Niche pick (user-selected): per-category memory by key prefix** — the Redis
  analog of the jcmd histogram. Sampler: periodic `SCAN MATCH <prefix>*` for counts +
  `MEMORY USAGE` on N samples, extrapolate avg×count per category. Confirmed prefixes
  in code: `bl:` (blacklist), `rt:fam:` (rotation families), `del:` (tombstones),
  `device:` / `ip:` (rate buckets, separate instance), plus `other`. Fixed category
  labels only.
- Token-accumulation early warning: `redis_db_keys_expiring` **trend** (the past
  "invalidated tokens pile up faster than they expire" overload), `INFO commandstats`
  + `latencystats` (Redis 7) per command — **EVALSHA** (rotation Lua) rate/p99
  specifically (single-threaded Redis: a slow Lua blocks everything),
  `used_memory/maxmemory` ratio + assert **noeviction** policy on the auth instance
  (evicting auth keys = auth incident).

### E. Node / instance
- node_exporter on all nodes: **PSI** (memory/cpu/io pressure — OOM precursor on 2GiB
  nodes), conntrack usage, context switches, CPU steal.
- **t4g burstable credits** (CW): CPUCreditBalance / CPUSurplusCreditBalance —
  credit exhaustion is silent performance collapse on this fleet.

### F. Traefik / entry
- Enable `--metrics.prometheus` AND access logs together (access logs are also the
  prerequisite for the unresolved 30s-hang investigation).
- blackbox exporter synthetic probes: each region probing the other + through GA/CF —
  the permanent version of the incident-day curl probes.

### G. DB depth (slow queries & skew)
- `mysql_global_status_slow_queries` rate (mysqld_exporter or perf_schema route).
- performance_schema `events_statements_summary_by_digest` top-N: per-digest calls,
  latency share, **rows_examined/rows_sent ratio**, tmp-disk-tables.
- Skew: `table_io_waits_summary_by_table` / `by_index_usage` (hot tables/indexes;
  bonus: usage-0 indexes = deletion candidates). Row-level hotness (the 034 vote
  counter escalation) has NO native metric — app-level top-K sketch only, never
  raw id labels.
- **Constraint:** performance_schema costs tens of MB of memory on a 1GiB t4g.micro
  that lives or dies by buffer pool hit ratio. Enable digest + table_io instruments
  only, and compare FreeableMemory before/after enabling. RDS Performance Insights is
  likely unsupported on this instance class — verify before planning around it.

### H. Distributed tracing (phase 2 of this task, after metrics)
- OpenTelemetry: Traefik v3 native tracing + Spring via micrometer-tracing/OTel agent
  + JDBC & Lettuce client spans (the ~130ms cross-region write becomes visible per
  request). GA emits no spans (pure L4) — infer its segment from parent/child gaps,
  mind cross-host clock skew.
- Per-hop network time = parent span duration − Σ(child span durations). Percentile
  subtraction across separate histograms is invalid — that's WHY tracing, not metric
  arithmetic, answers "which hop ate the latency."
- Exemplars to link histogram buckets → example traces. **Tail sampling** in the
  collector (keep slow traces; head sampling loses exactly the p99s you want).
  Sink: Grafana Cloud Tempo. Cheap precursor: Server-Timing response headers.

### I. Concurrency (the S in USE — was the missing column)
- In-flight: tomcat busy/max threads, in-flight requests gauge, MySQL
  **Threads_running** (not connected), Redis blocked_clients, SSE gauge.
- Contention: `jvm_threads_states{state="blocked"}`, MySQL `Innodb_row_lock_waits`
  / row_lock_time (the hot-row dial), deadlock counter; Redis contention shows up as
  EVALSHA latency (single-threaded).
- Saturation: thread-pool busy ratio, accept-queue depth, node run-queue + PSI cpu.
- **Little's law validation panel:** L (measured in-flight) vs λ×W. The 034
  PoolLedger sized pools from a *calculated* L (peak ~500 concurrent, 80/20 split);
  measuring L closes the loop on that assumption.
- Domain: idempotent-issuance hit counter = live frequency of the concurrent-refresh
  race that was fixed on 2026-07-13 (the bug's fix doubles as its own profiler).

## Alerts (minimum set, from incident history)
1. Seoul replica `DatabaseConnections == 0` sustained (15m) — **postmortem action item,
   due 2026-07-27.**
2. `absent(up{job=...})` for every scrape target (dead-man generalization).
3. Hikari `pending > 0` sustained.
4. Redis `used_memory/maxmemory` threshold + `keys_expiring` monotonic-growth.
5. CPUCreditBalance floor.
6. GTID gate primary-fallback ratio spike (replication lag became user-visible).

## Implementation order (suggested)
1. Micrometer config (B) + histogram flags — config-only, immediate value.
2. Exporters via GitOps (`deploy/base` + overlays): redis_exporter, node_exporter,
   Traefik metrics+access logs, blackbox (D/E/F bases).
3. Grafana Cloud wiring: Prometheus remote_write + CloudWatch datasource (A);
   dashboards; the 6 alerts above.
4. Niche sidecars (C jcmd exporter, D prefix sampler) — custom containers; respect the
   fixed-label cardinality rules; measure the observation cost you add.
5. DB depth (G) with the perf_schema memory check.
6. Tracing (H) as its own phase.

## Gotchas for the implementer (paid for today)
- SSM `--parameters` shorthand treats `[ ] ,` as metacharacters — use JSON form
  (`{"commands":["..."]}`). Bit us in the deploy pipeline.
- The repo is PUBLIC: no account IDs, instance IDs, or internal endpoints in committed
  manifests (registry host is injected at sync time via the ArgoCD kustomize images
  override; follow that pattern; internal hostnames go through the Route53 private
  zone like `mysql-replica.seoul.danteplanner.internal`).
- Every deploy rolls through the surge pipeline (ASG 1→2→1 with drain) — pods on the
  `role=app` nodes restart on every backend deploy; exporters on the data node
  (`role` label) don't.
- Observation cost is real on this fleet (2GiB nodes, 1GiB DB): every niche collector
  ships with a cadence, a scope bound, and a before/after resource check.
- DaemonSets ignore cordon (built-in toleration); NoExecute taints are how the deploy
  pipeline evicts — don't fight it in exporter design.

## Related artifacts
- 034 spec Observability section (`docs/tasks/034-multi-region-k8s-architecture/`)
- Postmortems: `~/postmortems/2026-07-13-*.md` (timelines + the layer-isolation
  checklist worth encoding into dashboards)
- Learning report: `~/learning/multi-region-architecture/07-advanced-infra-stack.md`
  (tracing/OTel background at depth)

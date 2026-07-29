# Phase 10 Ledger: Post-promotion local metric verification

Dossiers: none — no persistent agents spawned (see "Nature of phase").
Baseline: HEAD c131f156 (phases 01–09 committed). Branch `dev` (NOT `build/*`). The working tree
carries a heavy PRE-EXISTING dirty set unrelated to this phase and NOT this phase's to stage:
`.claude/*` (harness/skills/settings/commands), backend integration test sources
(CausalHarnessSupport/DegradationIT/HibernateInsertBatchingIT/MySQLIntegrationTest/
PlannerQueryCountTest/RedisConnectionRecoveryIT), backend/build.gradle.kts, deploy/base/
spring-daemonset.yaml, scripts/ops/lib/*, static submodule pointer, frontend vite.config.ts +
StartBuffMiniCard.test.tsx, requirements.md (M), phases/03 + phases/04 ledgers (M), and every
untracked docs/tasks/* + terraform/* + frontend/* path, plus plan/ (untracked task-level artifact).
Phase-10 delta at open = none (phases/10-ksm-prereq-live-verify did not exist).

## Nature of phase — live-only verification, docs-only repo artifact
Kind: live-only (verification against each region's LOCAL Prometheus after main promotion). There is
NO production/test code to author, NO local test suite (task brief: "Scoped run command: none local …
Do not manufacture a vacuous green"), and NO application behavior in the repo. The entire behavioral
deliverable is a set of live probes the USER runs against region-local Prometheus + `kubectl`, under
the live-only consent boundary. Accordingly:
- tdd-red / tdd-green / tdd-refactor burndown is INAPPLICABLE — no assertions exist to drive red, no
  code to write, no minimum-code debt to consolidate. Spawning a red test here would manufacture a
  vacuous green, which the brief forbids.
- No close-run: there is no scoped suite to run. The phase's regression net is the live probe record.
- No spec-verifier / no meme capture spawned here — spec verification beyond a close run and memory
  capture are TASK-LEVEL (owned by the /build orchestrator), not this phase manager's to run. Unlike
  the earlier-run phases 07/08 (which embedded a spec-verifier + capture leg), this phase manager's
  doctrine forbids spawning any verifier; phase-10's `verification.md` is therefore DEFERRED to
  task-level verification, and this manifest entry does not yet list a verification artifact.
- The pipeline collapses to: author this ledger (the operational verification runbook + record
  scaffolding) → docs-only stage (ledger + manifest.json) → phase closes `authored`, not `done`
  (every live probe is the user's, consent-gated) → the commit is the user's/orchestrator's; a staged
  proposal is returned, NOT run here (the branch is `dev`, not `build/*`).

## Doctrine deviation (surfaced) — in-repo ledger, no commit
- Phase-manager doctrine places the ledger in the state dir, never in the repo. This TASK's established
  convention (9 committed precedents: phases 01–09 each carry `phases/<id>-<slug>/ledger.md` in-repo)
  and my explicit launch instruction (in-repo ledger path) override it. The in-repo ledger IS the
  heartbeat; no redundant state-dir copy is written. Deviation surfaced for the merge gate.
- Doctrine forbids `git commit` on any branch not matching `build/*`. The current branch is `dev`.
  Consistent with phase 08 ("Commit is the USER's — proposal returned, not run here"), this phase
  STAGES the docs-only set and RETURNS the commit proposal; it does not commit.

## External contract (plan/phase-10.md)
After main promotion, each region's local Prometheus answers the allowlisted KSM metrics with values
matching `kubectl get nodes/pods`; `up{job="kube-state-metrics"} == 1` per region; the KSM head-series
delta stays within budget (INV3, ≤ ~2k per cluster). These checks use the REGION-LOCAL Prometheus and
do NOT depend on remote_write — fully completable without step-3 wiring.

## Live-only consent boundary
All probes below (kubectl, Prometheus queries, head-series measurements) are the USER's. They are given
as named, exact-command steps with concrete pass thresholds and blank record slots. The phase ships
`authored`, not `done`; the user acting on these steps after promotion is the point of the phase.

## Gating preconditions (ALL steps below gate on these — nothing is deployed yet)
`dev` has NOT been promoted to `main` at authoring time, so nothing new is deployed. Every probe gates on:
1. `dev` promoted to `main`.
2. ArgoCD has SYNCED the new manifests into both clusters — the phase-02 KSM Deployment/Service/RBAC/CRS
   ConfigMap AND the phase-01/04 `deploy/base/prometheus.yaml` scrape+relabel config.
3. The phase-04 Prometheus rollout has been RESTARTED so the new scrape jobs + `job` relabel are live
   (`kubectl -n <mon-ns> rollout restart deploy/prometheus` — or the region's Prometheus workload — then
   `rollout status`).
Run the whole runbook in BOTH regions: oregon AND seoul (one KSM Deployment per cluster; "per region").

## Operational runbook (USER-executed, consent-gated)

### STEP 0 — INV3 "before" baseline, capture BEFORE promotion (ORDERING TRAP)
The head-series "before" value MUST be captured while KSM is NOT yet deployed. Once promotion + rollout
land KSM's own series, the "before" is contaminated and the delta is unmeasurable. Capture it first, per
region, against the region-local Prometheus (Prometheus UI → Graph, or `curl` the HTTP API):
- PromQL (instant): `prometheus_tsdb_head_series`
- Record the value per region in the INV3 table below as `before`. If promotion already happened and no
  before-value was captured, INV3's delta is UNRECOVERABLE for this rollout — note it and measure the
  absolute post-value against the ~2k budget as a weaker check.

### STEP 1 — INV1 live: KSM has its own job label and is up (per region)
- PromQL (instant): `up{job="kube-state-metrics"}`
- Expected: exactly `1` (a single series, value 1) per region. A value of 1 confirms the phase-01 relabel
  gave KSM its own `job` label AND the target is being scraped. An empty result = relabel/annotation
  wiring failed or KSM not scraped; `0` = scraped but down.

### STEP 2 — INV3 after: head-series delta within budget (per region)
- PromQL (instant): `prometheus_tsdb_head_series` — capture as `after`.
- Pass: `after - before < ~2000` per cluster (INV3 cardinality budget). Record before/after/delta below.

### STEP 3 — KSM metrics match `kubectl` (per region)
Compare the allowlisted KSM series against ground truth from the cluster API:
- Node total — PromQL `count(kube_node_status_condition{condition="Ready",status="true"})`
  vs `kubectl get nodes --no-headers | wc -l`. Expected: EQUAL. (Pin `status="true"` — KSM emits one
  series per node PER status value {true,false,unknown}, so an unpinned `condition="Ready"` count is 3×
  node count; the `status="true"` series exists for every node with value 0 or 1, so counting them =
  total nodes.)
- Nodes Ready — PromQL `count(kube_node_status_condition{condition="Ready",status="true"} == 1)`
  vs `kubectl get nodes --no-headers | grep -cw Ready`. Expected: EQUAL.
- Pods Running — PromQL `count(kube_pod_status_phase{phase="Running"} == 1)`
  vs `kubectl get pods -A --field-selector=status.phase=Running --no-headers | wc -l`. Expected: EQUAL
  (allow a small skew if pods churn between the two reads; re-read if they disagree by more than churn).

### STEP 4 — INV4 metric-half: orphan-node Ready flip within one scrape (per region, mechanics §2.1 / §7 step 1)
Metric-half only — the rule-#7 firing half is Grafana-side and BLOCKED on remote_write (phase 09 / spec
defect), so do NOT expect a page here; this step proves only that the local scrape SEES the flip.
1. Pick a DISPOSABLE app node (`nodeSelector: role: data`-eligible worker, nothing irreplaceable on it).
2. Stop its agent: on that node, `sudo systemctl stop k3s-agent`.
3. Within one 30s scrape, PromQL `kube_node_status_condition{condition="Ready",status="true",node="<node>"}`
   flips `1 → 0` (and the `status="false"`/`status="unknown"` sibling series rises). Expected: the Ready
   series reads 0 within ~30s.
4. Clean up: `kubectl delete node <node>`, then restart the agent (`sudo systemctl start k3s-agent`) so the
   node re-registers, OR dispose of the node per normal decommission. Confirm the Ready series returns to 1
   (or the node object is gone) before leaving.

### STEP 5 — phase-02 CRS follow-up: etcd snapshot metric populates at RUNTIME (per region — control-plane etcd)
This CLOSES the phase-02 live-verify follow-up (manifest phase-02): the etcd CRS metric was render-verified
only; phase 09's etcd dead-man alert (G-etcd) is BLIND until this is confirmed at runtime.
- PromQL (instant): `kube_etcd_snapshot_creation_timestamp_seconds`
- Expected: at least one series present per region with a PLAUSIBLE recent unix timestamp — NOT absent, NOT
  `0`/epoch. Each `ETCDSnapshotFile` CR is its own object, so the metric returns MULTIPLE series (one per
  retained snapshot); test the NEWEST — freshness check `time() - max(kube_etcd_snapshot_creation_timestamp_seconds)`
  is small (below the live k3s snapshot interval; compare against the same interval phase-09's G-etcd rule
  uses, ~1.5× interval as the dead-man bound). A bare `time() - <metric>` yields a per-snapshot age and old
  retained snapshots will always look stale — use `max()` so an old snapshot is not misread as a failure. A present, fresh value confirms all three render-blind unknowns at once:
  (a) the CRS metric-name concatenation (metricNamePrefix + field) resolved to the exact allowlisted name
      `kube_etcd_snapshot_creation_timestamp_seconds` (else the allowlist silently drops it — phase-02 ledger);
  (b) the `status.creationTime` field path populated and KSM converted RFC3339 → unix float (a `0`/epoch or
      huge age would mean the field was empty and nilIsZero-style fabrication crept in — phase-02 avoided that);
  (c) CRS GA activation in KSM v2.19 emits custom-resource-state series at runtime.
- If ABSENT: G-etcd will never fire; capture this as a blocking finding for the phase-09 dead-man and the
  phase-02 follow-up — the render gate was semantically blind and cannot substitute.

## Record scaffolding (USER fills `Observed` / `Pass?` per region after promotion)

### INV3 head-series budget
| Region | before (step 0) | after (step 2) | delta | budget | Pass? |
|--------|-----------------|----------------|-------|--------|-------|
| oregon |                 |                | after−before | < ~2000 |     |
| seoul  |                 |                | after−before | < ~2000 |     |

### Per-check record
| # | Check | Region | Query / command | Expected | Observed | Pass? |
|---|-------|--------|-----------------|----------|----------|-------|
| 1 | INV1 up | oregon | `up{job="kube-state-metrics"}` | `== 1` |  |  |
| 1 | INV1 up | seoul  | `up{job="kube-state-metrics"}` | `== 1` |  |  |
| 3a | node total | oregon | `count(kube_node_status_condition{condition="Ready",status="true"})` vs `kubectl get nodes --no-headers \| wc -l` | EQUAL |  |  |
| 3a | node total | seoul  | `count(kube_node_status_condition{condition="Ready",status="true"})` vs `kubectl get nodes --no-headers \| wc -l` | EQUAL |  |  |
| 3b | nodes Ready | oregon | `count(kube_node_status_condition{condition="Ready",status="true"} == 1)` vs `kubectl get nodes --no-headers \| grep -cw Ready` | EQUAL |  |  |
| 3b | nodes Ready | seoul  | `count(kube_node_status_condition{condition="Ready",status="true"} == 1)` vs `kubectl get nodes --no-headers \| grep -cw Ready` | EQUAL |  |  |
| 3c | pods Running | oregon | `count(kube_pod_status_phase{phase="Running"} == 1)` vs `kubectl get pods -A --field-selector=status.phase=Running --no-headers \| wc -l` | EQUAL (±churn) |  |  |
| 3c | pods Running | seoul  | `count(kube_pod_status_phase{phase="Running"} == 1)` vs `kubectl get pods -A --field-selector=status.phase=Running --no-headers \| wc -l` | EQUAL (±churn) |  |  |
| 4 | INV4 Ready-flip (metric-half) | oregon | `kube_node_status_condition{condition="Ready",status="true",node="<node>"}` after `systemctl stop k3s-agent` | `1 → 0` within ~30s |  |  |
| 4 | INV4 Ready-flip (metric-half) | seoul  | `kube_node_status_condition{condition="Ready",status="true",node="<node>"}` after `systemctl stop k3s-agent` | `1 → 0` within ~30s |  |  |
| 5 | etcd CRS runtime | oregon | `kube_etcd_snapshot_creation_timestamp_seconds` + `time() - max(<metric>)` | present, fresh (below ~1.5× snapshot interval) |  |  |
| 5 | etcd CRS runtime | seoul  | `kube_etcd_snapshot_creation_timestamp_seconds` + `time() - max(<metric>)` | present, fresh (below ~1.5× snapshot interval) |  |  |

## Scenarios
| # | Scenario | Status | Red proof | Green proof |
|---|----------|--------|-----------|-------------|
| — | none — live-only verification phase; no testable local code (see "Nature of phase") | n/a | n/a | n/a |

## List revisions
- none — no burndown.

## Close
- burndown: not applicable (live-only; no code, no local tests).
- refactor: not applicable (no authored code to consolidate).
- close run: not applicable — no scoped suite exists (brief: "none local"). No vacuous green manufactured.
- verification.md: DEFERRED to task-level (spec-verifier is task-level; this phase manager spawns none).
- manifest (file classification — `git status --porcelain` minus Baseline):
  STAGED: phases/10-ksm-prereq-live-verify/ledger.md (new),
  docs/tasks/038-metrics-observability/manifest.json (M — phase-10 entry ONLY; diff verified to touch no
  other entry). NO code (live-only phase — nothing touches the repo).
  IGNORED: the entire pre-existing baseline-dirty set (see Baseline) — NOT this phase's; plan/ (untracked
  task-level artifact). No dossiers (none spawned).
- manifest status: phase 10 → authored (verdict: runbook authored; static certification + all live probes
  deferred — task-level verifier + user, consent-gated).
- staged: `git diff --cached --stat` = 2 files — phases/10-ksm-prereq-live-verify/ledger.md (new),
  docs/tasks/038-metrics-observability/manifest.json (M). Docs-only; no code. Commit is the USER's —
  proposal returned, not run here (branch is `dev`, not `build/*`).
- commit: NOT run (branch `dev` ≠ `build/*`; task convention returns a staged proposal). SHA: n/a.

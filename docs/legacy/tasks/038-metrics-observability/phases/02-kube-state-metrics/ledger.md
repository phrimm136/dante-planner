# Phase 02 Scenario Ledger: kube-state-metrics + etcd snapshot CR

Dossiers: $HOME/.local/state/claude-build/LimbusPlanner/038-metrics-observability/phase-02/
Baseline: HEAD 338f656 (phase-01 committed here). Pre-existing dirty tree (NOT this phase's to stage):
  tracked M on .claude/*, deploy/base/spring-daemonset.yaml, scripts/ops/lib/*,
  docs/tasks/038-.../requirements.md, static (submodule); untracked task scaffolding
  docs/tasks/038-.../plan/; plus many unrelated ?? task dirs. Phase-02 files clean at baseline:
  deploy/base/kube-state-metrics.yaml (new), deploy/base/kustomization.yaml (delta will surface them).
  Phase set = current porcelain minus this baseline.
Runner: `kubectl kustomize deploy/overlays/{oregon,seoul}` (kustomize binary absent; kubectl is fallback).

## Research-pinned facts (web-research-specialist, sourced)
- Image: current stable v2 minor = `registry.k8s.io/kube-state-metrics/kube-state-metrics:v2.19.1`
  (released 2026-06-12, GitHub releases/latest).
- `--metric-allowlist` DOES filter custom-resource-state metrics — confirmed in KSM
  `internal/store/builder.go`: both `buildStores` and `buildCustomResourceStores` apply the same
  `FilterFamilyGenerators`. A CRS metric NOT in the allowlist is silently dropped. => allowlist must
  carry the etcd CRS metric name as a 9th entry, else the etcd dead-man emits nothing (silent).
- ETCDSnapshotFile timestamp field = `status.creationTime` (`*metav1.Time`, RFC3339). KSM auto-converts
  RFC3339 → unix float for Gauge (no nanFromString). Do NOT use nilIsZero (field is omitempty; would
  emit epoch = fake huge age). Group `k3s.cattle.io`, version `v1`, kind `ETCDSnapshotFile`.
  BEST-EFFORT-FROM-DOCS: field path unverifiable by render test — phase 10 live-verify must confirm the
  CRS metric actually populates (render gate is semantically blind; it only proves tokens present).

## Acceptance
- Test: both overlays render clean AND carry all KSM contract tokens (render-grep, no persisted suite —
  CRITICAL RULE #1: no render-test harness exists, do not invent one; mirror phase 01).
- Opened RED (assertion) 2026-07-15 phase-open: all 9 KSM contract tokens = 0/0 in oregon+seoul baseline
  renders, both exit 0 (716/714 lines, clean tail `origin-client-ca`). Logs
  `/tmp/tdd-red-ksm-{oregon,seoul}-baseline.log`. Probed independently by phase-manager (3 tokens 0/0).
  Greenfield: the single baseline render is the RED proof for ALL 4 scenarios (tokens additive; nothing
  exists at baseline — mirrors phase 01). No per-scenario red spawns; each green gate re-renders and
  confirms this scenario's tokens present + prior tokens intact + LATER scenarios' tokens still absent
  (minimum-code guard against over-writing).
- Closed GREEN at scenario 4. Independent burndown-close run by phase-manager
  (`kubectl kustomize deploy/overlays/{oregon,seoul}` → `/tmp/pm-ksm-close-{oregon,seoul}.log`): both exit 0,
  clean tail `origin-client-ca`, ALL 9 acceptance tokens present 1/1 in both overlays (k3s.cattle.io 2/2 —
  ConfigMap gvk group + ClusterRole apiGroup). Renders grew 716→853 (oregon) / 714→851 (seoul), +137 lines
  each — KSM manifest is region-identical, as designed (base-only, no overlay diffs).

## Scenarios
| # | Scenario (one line) | Dossier | Status | Red proof | Green proof |
|---|--------------------|---------|--------|-----------|-------------|
| 1 | KSM SA+Deployment+Service render (nodeSelector role=data, replicas 1, image v2.19.1, req 10m/32Mi lim 64Mi, `--resources=nodes,pods,daemonsets`), registered in base kustomization | ksm-workload.md | closed | baseline 0/0 | S1 tokens =1/1 both overlays (image, resources arg, SA), later tokens still 0; render exit 0; probed by PM. Files: kube-state-metrics.yaml (new), kustomization.yaml (+1) |
| 2 | `--metric-allowlist` holds the 8 standard metrics (INV3 cardinality guard) | metric-allowlist.md | closed | baseline 0/0 | allowlist=1/1 both overlays, 8 metric names each =1, prior tokens intact, later (S3/S4) still 0; render exit 0; probed by PM. File: kube-state-metrics.yaml (+1 arg) |
| 3 | scrape-annotation wiring on pod template (scrape=true, port=8080, job=kube-state-metrics UNQUOTED) | scrape-annotations.md | closed | baseline 0/0 | UNQUOTED job token =1/1 both overlays, scrape/port present, prior intact, S4 tokens still 0; render exit 0; probed by PM. File: kube-state-metrics.yaml (+annotations) |
| 4 | etcd dead-man: RBAC trio (list,watch nodes/pods/daemonsets + etcdsnapshotfiles.k3s.cattle.io) + CRS ConfigMap (ETCDSnapshotFile, status.creationTime, metric `kube_etcd_snapshot_creation_timestamp_seconds`) mounted via `--custom-resource-state-config-file` + that metric added as 9th allowlist entry | etcd-deadman.md | closed | baseline 0/0 | FINAL: all 9 tokens present 1/1 both overlays (PM independent run), render exit 0. File: kube-state-metrics.yaml (+ClusterRole/Binding/ConfigMap/volume/9th allowlist). Benign divergence: token 8 count=1 (metricNamePrefix split emits concatenated name = allowlist entry — consistent) |

## Ratified Convention Decisions
- S3 scrape-annotation token form is UNQUOTED: `prometheus.io/job: kube-state-metrics` (NOT quoted).
  kubectl serializes plain-word annotation values unquoted (baseline render line 406:
  `prometheus.io/path: /actuator/prometheus`), quoting only bool/int-looking values (`port: "8080"`,
  `scrape: "true"`). Asserting the quoted job form would be perpetual-red. RATIFIED (primary-source
  verified by phase-open red). Carried into scenario-3 Brief. Directory-scoped? No — render-serialization
  quirk, not a deploy/ authoring rule; not CLAUDE.md-worthy (derivable from any render).

## List Revisions
- (pre-burndown, from research) Plan/mechanics §1 pin "eight-metric allowlist" is in tension with the
  required etcd CRS metric: KSM allowlist filters CRS metrics (confirmed in source). Spec is SILENT on
  the interaction (§1 covers standard metrics + RBAC; §4 covers the CRS route) — resolved under decision
  rights: allowlist = 8 standard + 1 CRS = 9 entries, with the CRS metric name pinned identically in the
  allowlist and the CRS config so they match. RATIFIED DECISION (surface for user). Alternative (drop the
  allowlist for the CRS metric) is impossible — no per-metric allowlist exemption exists.

## Pipeline (post-burndown)
- refactor: nothing to consolidate — all 4 scenarios were minimal additive edits to ONE new file
  (deploy/base/kube-state-metrics.yaml); all tdd-green strain reports = none. Single cohesive manifest
  following the prometheus.yaml exemplar (label/nodeSelector/resources/Service-targetPort conventions);
  no cross-file duplication to extract (deploy/CLAUDE.md keeps KSM base-only, no region variants). No
  refactor spawn (would produce zero edits) — same call as phase 01.
- verify: PASS (1 round) — phases/02-kube-state-metrics/verification.md. All Mechanics §1 + §4-etcd +
  INV1/INV3/INV11 render-local halves MET in both overlays; both CRS wiring joins (allowlist entry ==
  KSM-emitted name; config-file path == mountPath+volume+ConfigMap key) byte-verified; INV11 secret scan
  clean. Live/drill/CRS-runtime halves correctly DEFERRED-OUT-OF-SCOPE to phases 09/10.
- capture: drafts UNAVAILABLE — `meme draft --from-phase` (both task path and dossier dir) TIMED OUT at
  2min (exit 143), likely LLM-distillation latency. Non-blocking per doctrine; residue preserved in this
  ledger (research facts, ratified decisions, list revision) and the dossiers' `## Learnings` for a later
  manual draft. sweep: RAN (watermark → 338f656) — all ~30 candidates are unrelated global lessons (Redis/
  latency/TS/Zod/React/etc.); NONE touch this phase's KSM kustomize diff → all still-true, 0 stale, 0
  obsolete. No stale-doc edits. No deploy/CLAUDE.md dir-rule: both ratified decisions are render-quirk /
  single-file-KSM-specific (deepest governed dir = one manifest → meme's domain, not a directory rule).
- manifest: STAGED — deploy/base/kube-state-metrics.yaml (new), deploy/base/kustomization.yaml (M, +1
  registration line), phases/02-kube-state-metrics/{ledger.md, verification.md} (new), manifest.json (M,
  phase-02 entry → done/PASS + artifacts + live-verify followUp). IGNORED (pre-existing baseline, surfaced
  to user, NOT this phase's): deploy/base/spring-daemonset.yaml (M), docs/tasks/038-.../requirements.md (M),
  docs/tasks/038-.../plan/ (?? task scaffolding — same as phase 01; user may fold in). No test debris, no
  submodule pointer moved by this phase (static submodule untouched).
- staged: 5 files, 277 insertions / 2 deletions — kube-state-metrics.yaml (+120, new),
  kustomization.yaml (+1), manifest.json (9 changed: phase-02 entry), ledger.md (+88, this file),
  verification.md (+61). Matches the manifest staged set exactly; baseline dirt (spring-daemonset.yaml,
  requirements.md, plan/) NOT staged.

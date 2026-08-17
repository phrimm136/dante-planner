# Phase 01 Scenario Ledger: Prometheus prerequisites

Dossiers: $HOME/.local/state/claude-build/LimbusPlanner/038-metrics-observability/phase-01/
Baseline: HEAD 1f88e1e0. Pre-existing dirty tree (~37 entries, NOT this phase's to stage): tracked M on
  .claude/*, deploy/base/spring-daemonset.yaml, scripts/ops/lib/*, docs/tasks/038-.../requirements.md,
  static (submodule); untracked task scaffolding docs/tasks/038-.../{manifest.json, plan/}; plus many
  unrelated ?? task dirs. Phase-01 files clean at baseline: deploy/base/prometheus.yaml,
  deploy/overlays/{oregon,seoul}/kustomization.yaml (delta will surface them). Phase set = current
  porcelain minus this baseline.
Runner: `kubectl kustomize deploy/overlays/{oregon,seoul}` (kustomize binary absent; kubectl is fallback).

## Acceptance
- Test: both overlays render clean AND carry all 5 contract tokens region-correctly (render-grep, no
  persisted suite — CRITICAL RULE #1: no render-test harness exists, do not invent one).
- Opened RED (assertion) 2026-07-15T03:36:02Z: all 5 tokens = 0 matches in both oregon+seoul baseline
  renders (`/tmp/tdd-red-{oregon,seoul}-baseline.log`). Closed GREEN at scenario 4.
- Independent scoped-suite run (phase-manager, burndown close): `kubectl kustomize deploy/overlays/{oregon,seoul}`
  both exit 0; oregon+seoul each render T1:1 T2:1 T3:1 T4:1 T5:1 localhost:1; env vacuity holds
  (oregon=oregon ¬seoul, seoul=seoul ¬oregon). Render tails end at origin-client-ca (clean multi-doc output).
- Structural parse (python3 yaml.safe_load of the embedded `prometheus.yml` block-scalar — greps prove
  substrings, not YAML validity): PASS both overlays. `global.external_labels.cluster == ${CLUSTER_NAME}`;
  io_job relabel is a genuine element of `backend.relabel_configs`; `prometheus` is a TOP-LEVEL
  `scrape_configs` job (not nested inside backend) with `static_configs` target `localhost:9090`.

## Scenarios
| # | Scenario (one line) | Dossier | Status | Red proof | Green proof |
|---|--------------------|---------|--------|-----------|-------------|
| 1 | base ConfigMap external_labels `cluster: ${CLUSTER_NAME}` + Deployment arg `--enable-feature=expand-external-labels` | cluster-external-label.md | closed | T1+T2 = 0/0 in oregon+seoul baseline | T1=1,T2=1 both overlays, render clean, delta=prometheus.yaml only |
| 2 | overlay patches set Deployment env `CLUSTER_NAME` (oregon=oregon, seoul=seoul) + registered in both kustomizations; vacuity: oregon !renders seoul, seoul !renders oregon | region-cluster-name-patch.md | closed | T5 CLUSTER_NAME = 0/0 baseline | oregon env=oregon(¬seoul), seoul env=seoul(¬oregon), S1 intact, delta=2 patches+2 kustomizations |
| 3 | `prometheus_io_job` relabel appended to pod-discovery relabel_configs | job-identity-relabel.md | closed | T3 = 0/0 baseline | T3=1 both overlays (target_label: job), prior tokens intact, delta=prometheus.yaml only |
| 4 | static `prometheus` self-scrape job vs `localhost:9090` | prometheus-self-scrape.md | closed | T4 = 0/0 baseline | T4=1 + localhost:9090 both overlays, ALL 5 tokens region-correct, delta=prometheus.yaml only |

Note: region-correctness (scenario 2) asserts on the Deployment env var, NOT the rendered ConfigMap —
kustomize renders `cluster: ${CLUSTER_NAME}` literally; Prometheus expands it at runtime from the env
var (expand-external-labels). Asserting `cluster: oregon` in the render would be perpetually red.

## List Revisions
- (none yet)

## Pipeline (post-burndown)
- refactor: nothing to consolidate — all 4 scenarios were minimal additive config appends; tdd-green
  strain reports = none. The two per-overlay patch files differ only by the mandated region value
  (deploy/CLAUDE.md: region differences live in overlays, never base) — not extractable duplication.
  No refactor spawn (would produce zero edits).
- verify: PASS (1 round) — phases/01-prometheus-prereqs/verification.md. All 4 in-scope items MET;
  vacuity guard PASS; live/drill halves correctly deferred (phases 02/09/10).
- capture: drafts 18 (task) + 2 (dossiers: kustomize-binary-absent gotcha, phased-prometheus-config
  decision). sweep: all candidates still-true w.r.t. this phase's diff (Prometheus kustomize config
  touches none of the surfaced facts — mostly unrelated FE/Redis/latency lessons); 0 stale docs, 0
  obsolete. No CLAUDE.md edits (deploy/CLAUDE.md "region differences live in overlays" already governs
  and was followed; expand-external-labels literal-render is too narrow/derivable for a dir rule).
- manifest: STAGED — deploy/base/prometheus.yaml, deploy/overlays/{oregon,seoul}/kustomization.yaml,
  deploy/overlays/{oregon,seoul}/prometheus-cluster-patch.yaml, phases/01-prometheus-prereqs/{ledger.md,
  verification.md}, manifest.json. IGNORED — deploy/base/spring-daemonset.yaml (pre-existing baseline M);
  plan/ + requirements.md + all other baseline dirty entries (pre-existing, surfaced to user, not this
  phase's); .meme/drafts/* (gitignored). NOTE: plan/ is baseline task scaffolding — user may fold into
  this commit if desired.
- staged: 8 files, 172 insertions — deploy/base/prometheus.yaml (+10), oregon/kustomization.yaml (+1),
  oregon/prometheus-cluster-patch.yaml (+12), seoul/kustomization.yaml (+1),
  seoul/prometheus-cluster-patch.yaml (+12), manifest.json (+47, was untracked → whole file),
  ledger.md (+54), verification.md (+35). Matches manifest staged set; spring-daemonset.yaml NOT staged.

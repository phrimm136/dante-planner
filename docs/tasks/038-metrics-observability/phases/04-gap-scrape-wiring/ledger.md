# Phase 04 Scenario Ledger: Gap-cluster scrape wiring

Dossiers: ~/.local/state/claude-build/LimbusPlanner/038-metrics-observability/phase-04/ (unused — phase blocked before any spawn)
Baseline: pre-existing dirty tree (unrelated to this phase); HEAD b9002587. Phase-04 delta at open = none.

## Status: BLOCKED — spec/scope contradiction (no code authored, nothing staged)

## Phase contract (plan/phase-04.md)
Extend the CP bootstrap patch loop in `terraform/modules/fleet/user-data/cp.sh.tftpl` so four gap
clusters are scraped under their own `job` labels in both regions:
1. ArgoCD application-controller (`:8082`, job `argocd`)
2. ESO controller (`:8080`, job `external-secrets`)
3. k3s server metrics — CP/etcd health (apiserver request latency + etcd fsync)
4. CoreDNS (job `coredns`)
File scope: `cp.sh.tftpl` ONLY. Method: idempotent `kubectl patch` in the existing patch-loop pattern
(exemplar `cp.sh.tftpl:79-80`). Depends on phase-01 job relabel. No local tests (infra); INV8 is the live drill.

## Blocking finding — target 3 (CP/etcd health) is unsatisfiable within the phase file scope
Phase-01's `deploy/base/prometheus.yaml` scrape_configs (read at HEAD b9002587) contain exactly two jobs:
- `backend` — `kubernetes_sd_configs: [role: pod]`; keeps only pods carrying
  `prometheus.io/scrape: "true"`, job set from `__meta_kubernetes_pod_annotation_prometheus_io_job`.
- `prometheus` — static `localhost:9090` self-scrape.
There is NO `role: endpoints` or `role: node` discovery job.

k3s apiserver and embedded etcd run as the k3s **server host process** on the CP node, NOT as pods
(k3s runs them in-process — unlike kubeadm there is no static-pod manifest to annotate). Therefore:
- Pod-annotation discovery (the entire mechanism phase-04 is scoped to) cannot reach a non-pod endpoint.
- A bootstrap-created headless Service + manual Endpoints would NOT be scraped either — no `role: endpoints`
  job exists to discover it.
- Wiring CP/etcd health requires adding a scrape_config job to `deploy/base/prometheus.yaml` — OUT of
  phase-04's file scope (that file is phase-01's, and phase-01 is `done`).
- apiserver `:6443/metrics` additionally needs bearer-token auth, which the annotation-relabel scheme
  cannot carry (a hostNetwork placeholder pod is a trap, not a fix).

The plan's mechanics §4 CP/etcd row ("wiring detail settled at implementation within the annotation/job
conventions above") presumes the annotation route reaches the k3s server; it does not. This is a
spec/scope contradiction, not an implementation choice — escalated rather than guessed.

### Supporting facts (sharpen the user's options)
- `cp.sh.tftpl:39-50` k3s server args have NO `--etcd-expose-metrics=true` — etcd metrics on `:2381`
  are not even emitted yet. That enablement IS in `cp.sh.tftpl` scope; the missing scrape job is not.
- `prometheus.yaml:13-15` ClusterRole already grants `nodes/metrics` + `endpoints` — permissions are
  NOT the blocker; the only missing piece is the scrape_config job. Expanding scope is cheaper than it
  looks, and phase-01 appears to have under-delivered the endpoints/node discovery the original design implies.

## Targets 1, 2, 4 — authorable, but NOT authored (whole-phase block)
ArgoCD (statefulset pod), ESO (deployment pod), CoreDNS (kube-system deployment pod) are all pods; their
mechanism is fully settled by phase-01's ratified pod-annotation convention and is identical under either
resolution option. They were NOT authored: the phase's external contract names all four; one unsatisfiable
target means the contract is unmet, so the phase blocks as a unit (doctrine has no partial-author mode).
CoreDNS carries a verify-first (does k3s v1.31 bundled CoreDNS already carry `prometheus.io/*` annotations
sweeping it into `job=backend`? → patch must OVERRIDE the job annotation, not merely add) — deferred to the
corrected pass.

## Resolution options handed to the user
- A — Expand phase-04 file scope to include `deploy/base/prometheus.yaml` (add an apiserver/etcd
  scrape_config job) + the `--etcd-expose-metrics=true` k3s flag in `cp.sh.tftpl`; re-run all four targets together.
- B — Split CP/etcd health into its own phase (deps on 01); phase-04 delivers ArgoCD/ESO/CoreDNS now.
Under either ruling the three pod targets are ready to author immediately; the block is scoped to one leg,
not the phase's viability.

## Pipeline
- burndown: not started (blocked at brief-authoring on spec/scope contradiction)
- refactor / verify / capture: not reached
- manifest: phase 04 → blocked; no other entry touched
- staged: nothing (per Failure Handling)

---

## RESUMED 2026-07-15 — scope ruling landed (option A)
Baseline (resumed): HEAD b9002587 (unchanged). Working tree still carries the pre-existing dirty set
(unrelated to this phase) PLUS this phase's own prior residue: manifest.json (M — phase-04 blocked
entry only; verified `git diff` touches no other phase), this ledger + phases/04 dir, and plan/ (untracked
task-level artifact — NOT this phase's to stage). Phase-04 delta to author from here:
deploy/base/prometheus.yaml (M), terraform/modules/fleet/user-data/cp.sh.tftpl (M).

Orchestrator ruling: option A. plan/phase-04.md amended — file scope now includes deploy/base/prometheus.yaml
(NEW dedicated apiserver :6443 bearer-token + etcd :2381 scrape jobs, strictly additive to phase-01's
committed config) and `--etcd-expose-metrics=true` in cp.sh.tftpl. The CP/etcd-out-of-scope block is
RESOLVED; the investigation above stands as its record.

Infra phase, no local test suite (plan: "Tests: none local"). tdd-red/tdd-green burndown is inapplicable
— there are no assertions to drive red. Pipeline adapts to authored-artifact legs, each gated by a
regression net, then static spec-verification; the live drill (INV8) + live apply are consent-gated to
the user (phase closes `authored`, not `done`).

Regression nets (baseline confirmed GREEN this session):
- prometheus.yaml: `kubectl kustomize deploy/overlays/oregon` (932 lines) && `.../seoul` (927 lines) render clean.
- cp.sh.tftpl: `bash -n` GREEN; shellcheck available (catches unbalanced quotes in nested-single-quote kubectl patch -p strings — the real authoring risk). No promtool → prometheus config is NOT locally semantic-validatable; spec-verifier static review + the live drill are the correctness checks.

## Artifacts (authored legs)
| # | Artifact | Dossier | Status | Gate proof |
|---|----------|---------|--------|-----------|
| A | cp.sh.tftpl: patch-loop annotations (ArgoCD/ESO/CoreDNS) + --etcd-expose-metrics=true | cp-tftpl.md | done | bash -n exit 0 + shellcheck -S error exit 0 (my probe) |
| B | prometheus.yaml: apiserver + etcd scrape jobs + nonResourceURLs /metrics ClusterRole rule (additive) | prometheus-scrape.md | done | kubectl kustomize oregon(960)+seoul(955) exit 0; grep confirms job_name apiserver/etcd/backend/prometheus + nonResourceURLs all present (my probe) |

Advisor-caught addition folded into B before spawn: apiserver bearer-token /metrics scrape needs a
`nonResourceURLs: ["/metrics"]` get grant (own ClusterRole rule) — plan-preauthorized, invisible to
kustomize, would 403 at the live drill. Authored + render-confirmed.

## Pipeline (post-burndown)
- authored legs A+B: both gates GREEN (probes above). Agents touched exactly the two target files;
  `deploy/base/spring-daemonset.yaml` in `git diff` is BASELINE-dirty (M at phase open), NOT this phase's.
- refactor: nothing to consolidate — no burndown = no accreted minimum-code debt; two additive infra
  edits, each already final-form. tdd-refactor leg skipped by design (infra).
- scoped "suite" (run by me, the phase's independent execution — no unit suite exists for infra):
  `bash -n cp.sh.tftpl` exit 0; `shellcheck -S error cp.sh.tftpl` exit 0;
  `kubectl kustomize deploy/overlays/oregon` exit 0 (960 lines); `.../seoul` exit 0 (955 lines).
  Rendered scrape_configs carry job_name backend(L204)/prometheus(L225)/apiserver(L228)/etcd(L239)
  + ClusterRole nonResourceURLs(L69). No promtool → prometheus semantic validation deferred to live drill.
- verify: PASS after 1 round — 6/6 STATIC contract items MET, 3 DEFERRED-LIVE (live scrape both
  regions, INV8 drill, etcd :2381 cross-node reachability) — phases/04-gap-scrape-wiring/verification.md
- capture: 25 drafts from task (gitignored, indexed for dedup), 0 non-derivable from dossiers
  (agent Learnings were derivable — relabel style / etcd keep-regex rationale). sweep reviewed ~40
  candidates (watermark → b9002587); NONE made stale/obsolete by this phase's two-file diff (all
  surfaced facts are unrelated FE/Redis/Zod/latency/git lessons). No docs edited, no retire proposals.
- ratified convention decisions (ledger-only, not directory-scope → no CLAUDE.md rule):
  (1) apiserver keep relabel uses the canonical `default;kubernetes;https` triplet;
  (2) etcd control-plane keep uses `regex: .+` (presence check on the control-plane node label)
  over literal `true` — robust to any truthy value, still keeps only labeled server nodes.
- manifest (file classification — `git status --porcelain` minus Baseline):
  STAGED: deploy/base/prometheus.yaml (M, artifact B), terraform/modules/fleet/user-data/cp.sh.tftpl
  (M, artifact A), docs/tasks/038-metrics-observability/manifest.json (M — phase-04 entry only,
  diff verified), phases/04-gap-scrape-wiring/ledger.md (new), phases/04-gap-scrape-wiring/verification.md (new).
  IGNORED: all pre-existing baseline-dirty paths (.claude/*, deploy/base/spring-daemonset.yaml,
  scripts/ops/*, static submodule pointer, docs/RUNBOOK.md, other docs/tasks/*, terraform/rds/plantf,
  terraform/seoul/tfplan, frontend/*) — NOT this phase's; plan/ (untracked task-level plan artifact,
  whole task — surfaced in return: plan/phase-04.md carries the option-A amendment authorizing this
  diff); .meme/drafts (gitignored capture output). Dossiers live outside the repo — never staged.
- manifest status: phase 04 → authored (verdict PASS static; live drill consent-gated to user)
- staged: [git diff --cached --stat digest — pending add]

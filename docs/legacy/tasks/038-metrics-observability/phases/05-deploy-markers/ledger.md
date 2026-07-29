# Phase 05 Scenario Ledger: Deploy-marker annotations

Dossiers: ~/.local/state/claude-build/LimbusPlanner/038-metrics-observability/phase-05/
Baseline: HEAD 1fd93505. Working tree carries a large PRE-EXISTING dirty set unrelated to this
phase (.claude/*, deploy/base/spring-daemonset.yaml, scripts/ops/*, static submodule pointer,
requirements.md M, phases/03+04 ledgers M, many untracked docs/tasks/* and terraform/* — NONE this
phase's). manifest.json is CLEAN at open. plan/ is an untracked TASK-LEVEL artifact (whole dir `??`)
— NOT this phase's to stage. Phase-05 delta to author from here: .github/workflows/deploy-fleet.yml (M).

## Kind: live-only — adapted pipeline (no red/green burndown)
Plan: "Tests: none local; verification is the next production rollout." Deliverable is a single
workflow CODE edit (spec-defect footnote in plan/phase-05.md: Done-When item 7 tags this live-only,
yet the artifact is a `deploy-fleet.yml` edit with no local render/red-green home — honored live-only
because the only proof is a live rollout). tdd-red/tdd-green burndown is inapplicable: no assertions
to drive red. Pipeline adapts (phase-04 precedent) to ONE authored-artifact leg via a spawned
tdd-green in authoring scope, gated by a syntactic regression net, then static spec-verification;
live provisioning + the rollout verification are consent-gated to the USER → phase closes `authored`.

Regression net (local ceiling — no actionlint/yamllint installed):
- Whole-file YAML well-formedness: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/deploy-fleet.yml'))"`.
- Extracted marker run-block shell: `shellcheck -S error` on the run: script (the real authoring risk —
  quoting inside the JSON payload / curl). pyyaml cannot see `${{ }}` semantics; shellcheck needs the
  extracted shell, not the whole file. The true correctness check is the user's live rollout.

## Settled decisions (each restated in the Brief as fact)
- D1 pipeline: authored-leg via tdd-green; gate = pyyaml + shellcheck; close `authored`.
- D2 placement: insert the marker step AFTER "Wait for rollout on both clusters" and BEFORE
  "Drain surge nodes and scale back to 1" in the `settle-down` job. Trigger is rollout success:
  positional ordering skips the marker if rollout failed (correct) and still posts it if surge-cleanup
  later fails — the deploy DID complete, and drain-failure is exactly the incident window a marker
  serves ("surge stays up, investigate"). CONSCIOUS divergence from the plan's literal "last step of
  settle-down" wording toward its explicit semantic condition "after rollout status succeeds for both
  clusters"; recorded, not silent.
- D3 tag value: region/cluster tag = the Prometheus `cluster` external-label set by phase-01 overlays
  (`oregon`, `seoul` — deploy/overlays/{oregon,seoul}/prometheus-cluster-patch.yaml), NOT the AWS
  region codes the workflow loops on. Markers must align to the per-cluster alert timelines they
  annotate. One POST per cluster.
- D4 secrets: `secrets.GRAFANA_ANNOTATIONS_TOKEN` (Grafana service-account Bearer token — INV11 secret).
  Grafana instance base URL is NOT an INV11 secret (INV11 = token/webhook/DSN/account-id) → carried as
  a non-secret GitHub Actions repository variable `vars.GRAFANA_URL` (e.g. https://<slug>.grafana.net).
- D6 non-fatal: POST failure emits `::warning::` and does NOT fail the job — observability must not
  break a good deploy.
- D7 security: token+URL passed via the step `env:` block, referenced as `$GRAFANA_TOKEN` / `$GRAFANA_URL`
  in `run:` — never `${{ }}` interpolated inside the run block (.github/workflows/CLAUDE.md). SHA from
  the existing job-env `$GIT_SHA` (= github.sha). No untrusted PR input.
- D8 payload: `POST ${GRAFANA_URL}/api/annotations`, Bearer auth, JSON
  `{"tags":["deploy","cluster:<c>","sha:<GIT_SHA>"],"text":"...","time":<epoch_ms>}`, global annotation
  (no dashboardUID → spans dashboards). Loop over the two clusters (oregon, seoul).

## Acceptance
- Live-only: no local acceptance test. Verification = a deploy-marker annotation appears in Grafana on
  the next production rollout (Done-When item 7) — consent-gated to USER, recorded as named steps in the
  return's COMMIT PROPOSAL.

## Artifacts
| # | Artifact | Dossier | Status | Gate proof |
|---|----------|---------|--------|-----------|
| A | deploy-fleet.yml: marker POST step in settle-down (per-cluster Grafana annotation) | deploy-marker.md | done | my probes: pyyaml `yaml OK`; `git diff --stat` = 1 file +19 (only the workflow); hunk @291 confirms step AFTER "Wait for rollout" / BEFORE "Drain surge nodes" (D2); INV11 local-half OK — only `${{ secrets.GRAFANA_ANNOTATIONS_TOKEN }}` + `${{ vars.GRAFANA_URL }}`, no literal token/URL, env-indirection in run, loop-literal/`$GIT_SHA` only |

## List Revisions
- (none) — single authored artifact, no burndown to reshape.

## Divergences accepted
- tdd-green added `curl --fail` (Brief D6 listed only `--silent --show-error`). ACCEPTED as a correct
  improvement: without `--fail`, curl exits 0 on HTTP 4xx/5xx and the D6 `|| echo "::warning::"` branch
  would never fire (would catch only connection failures). Still non-fatal (the `||` absorbs the exit).

## Pipeline (post-authoring)
- authored leg A: GREEN (probes above).
- refactor: skipped by design — live-only/infra, no burndown = no accreted minimum-code debt; one
  additive final-form step. tdd-refactor leg not spawned (phase-04 precedent).
- verify: PASS after 1 round (static) — statically-certifiable items MET, live token/marker DEFERRED-LIVE
  — phases/05-deploy-markers/verification.md
- capture: 28 drafts from task + 5 from dossiers (deploy-marker specifics: heredoc-at-col-0 gotcha,
  step-placement decision, non-fatal decision, Grafana annotations API gotcha, cluster-tag gotcha) —
  all gitignored. sweep reviewed 59 candidates (watermark → 1fd93505): NONE made stale/obsolete by this
  phase's 19-line workflow diff (all unrelated FE/Zod/Redis/git/latency/spec facts). No docs edited, no
  retire proposals. No directory-scope convention decision (tdd-green: CONVENTION DECISIONS none).
- manifest (git status --porcelain minus Baseline):
  STAGED: .github/workflows/deploy-fleet.yml (M, artifact A), manifest.json (M — phase-05 entry only,
  diff verified), phases/05-deploy-markers/ledger.md (new), phases/05-deploy-markers/verification.md (new).
  IGNORED (not this phase's): all pre-existing baseline-dirty paths (.claude/*, deploy/base/spring-daemonset.yaml,
  scripts/ops/*, static submodule pointer, requirements.md M, phases/03+04 ledgers M, terraform/*, frontend/*,
  the many untracked docs/tasks/*); plan/ (untracked TASK-LEVEL plan dir — whole task, not this phase);
  .meme/drafts (gitignored capture output). Dossiers live outside the repo — never staged.
- manifest status: phase 05 → authored (verdict PASS static; live rollout consent-gated to user).
- staged: git diff --cached --stat = 4 files — see COMMIT PROPOSAL. Matches STAGED set exactly.

# Phase 01 (infra)

## Rows
- harness-selftest-reproduces-baseline: scripts/perf/selftest.mjs — selftest.mjs executes the sweep + profile against a local preview
- harness-deps-isolated: scripts/perf/package.json — the FE dependency graph is inspected

## Touches
- scripts/perf/**

## Validation
- `node scripts/perf/selftest.mjs`

## Seams (drives)
- frontend/package.json -> no lighthouse/playwright dependency
- selftest.mjs(current build) -> metrics within tolerance of baselines.json

## DECISION SLOTS
SLOT ordering-rationale: Build shared `lib/` (preview lifecycle, seed-a-plan, CDP presets, percentile math) first, then the scripts (sweep, eager-gate, profile, geometry, inp), then `baselines.json` (transcribe mechanics §1/§1b/§1c), then `selftest.mjs` last since it composes all of them. No migrations. `harness-deps-isolated` is satisfied the moment the isolated `scripts/perf/package.json` exists; `selftest` is the terminal probe.
SLOT batch-boundary: The two rows are independent terminal verifications, not a red-first unit batch — `harness-deps-isolated` is an arch grep (green once the isolated package.json lands), `harness-selftest-reproduces-baseline` is a green-only live probe against the current build. Build the suite, then run each once; no shared red batch.
SLOT fallback-scope: Sole stub surface is `scripts/perf/**`. If the env spike `probe-harness-env` is dead (no Chromium/CDP), land `lib/` + `baselines.json` + `eager-gate.mjs` first (the one gate /build wires in — needs no browser), and defer the browser-driven sweep/profile/geometry/inp + selftest behind the spike verdict.

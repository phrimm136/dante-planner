# Phase 09 (live-only)

## Rows
- mobile-budgets-met: scripts/perf/sweep.mjs — the Lighthouse sweep runs
- plan-content-p50-improved: scripts/perf/profile.mjs — the plan-route profile runs

## Touches

## Validation
- `node scripts/perf/sweep.mjs && node scripts/perf/profile.mjs && node scripts/perf/geometry.mjs`

## Seams (drives)
- profile.mjs(post-remediation) -> plan-route content p50 below the §1b baseline
- sweep.mjs(post-remediation build) -> mobile budgets

## DECISION SLOTS
SLOT ordering-rationale: Terminal verification, deps on all 8 prior phases. Build once with the §6 same-origin config, bring up the full stack (spike `probe-full-stack-measure`), then run `sweep.mjs` (mobile budgets) → `profile.mjs` (plan-route p50) → `geometry.mjs` (skeleton deltas, INV9-10). Both rows read the same post-remediation build — build once, measure many.
SLOT batch-boundary: Both rows are green-only live measurements, not red-first — they verify the aggregate outcome after all remediation lands. No red batch; one measurement run produces both verdicts against the mechanics §1/§1b baselines.
SLOT fallback-scope: No stub surface (touches empty). If the stack cannot come up (`probe-full-stack-measure` dead), this phase blocks at the task-tier join (taskLevel INV8/INV11) rather than stubbing — a live budget cannot be faked. Report the blocker; the remediation phases still stand on their unit-tier proofs.

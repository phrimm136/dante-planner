# Phase 06 (local-tdd)

## Rows
- home-async-section-skeleton-fixed-height: frontend/src/pages/home/__tests__/HomePage.test.tsx#async-section-fallback-reserves-fixed-height — its Suspense fallback renders

## Touches
- frontend/src/pages/home/**
- frontend/src/pages/planner/PlannerMDGesellschaftPage.tsx
- frontend/src/components/feedback/LoadingState.tsx

## Validation
- `yarn --cwd frontend vitest run src/pages/home src/components/feedback`

## Seams (drives)
- home async section fallback -> shaped skeleton with fixed min-height, not LoadingState

## DECISION SLOTS
SLOT ordering-rationale: Single row, single seam group (home + gesellschaft async sections + `LoadingState`). Replace the page-/section-level `LoadingState` fallbacks with shaped fixed-height skeletons; the row asserts the home fallback reserves fixed height and is not a `LoadingState` text box. The gesellschaft page shares the same fallback pattern — fix both under the one assertion.
SLOT batch-boundary: One row, one red-green cycle. CLS confirmation is deferred to the phase-09 live sweep (INV8) — this phase proves the structural fix (fixed-height skeleton) at unit tier only.
SLOT fallback-scope: No stub surface. If blocked, ship the home skeleton first (the measured 0.38 CLS offender) and defer the gesellschaft-section skeleton — but the live CLS row (phase 09) still gates both.

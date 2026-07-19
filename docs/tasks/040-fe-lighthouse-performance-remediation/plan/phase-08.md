# Phase 08 (local-tdd)

## Rows
- router-preload-intent: frontend/src/lib/__tests__/router.test.tsx#defaultPreload-is-intent — its options are read
- plan-detail-shell-first: frontend/src/pages/planner/__tests__/PlannerMDDetailPage.test.tsx#header-renders-before-deferred-heavy-sections — the first commit renders
- grid-skeleton-count-from-pagesize: frontend/src/components/feedback/__tests__/ListPageSkeleton.test.tsx#planner-grid-count-derives-from-page-size — it renders its default card count
- deck-fallback-uses-card-dimensions: frontend/src/pages/planner/components/plannerViewer/__tests__/GuideModeViewer.test.tsx#deck-fallback-tiles-use-card-grid-dimensions — its fallback renders
- planner-card-constant-single-source: frontend/src/lib/__tests__/constants.test.ts#planner-card-height-single-source — their heights are compared

## Touches
- frontend/src/lib/router.tsx
- frontend/src/pages/planner/components/plannerViewer/**
- frontend/src/components/feedback/ListPageSkeleton.tsx
- frontend/src/lib/constants.ts

## Validation
- `yarn --cwd frontend vitest run src/lib src/pages/planner src/components/feedback`

## Seams (drives)
- Deck Builder Suspense fallback -> tile dimensions from CARD_GRID constants, not w-16 h-20
- PlannerGridSkeleton() -> cardCount derived from PLANNER_LIST.PAGE_SIZE, not hardcoded 8
- plan detail render -> header from loader data in first commit; deck/floors/notes behind deferred skeletons
- planner card height -> constant and rendered card consume one source (CARD_GRID.HEIGHT)
- router config -> defaultPreload === 'intent'

## DECISION SLOTS
SLOT ordering-rationale: Order by seam independence. (1) `router-preload-intent` — one-line router option, standalone. (2) `planner-card-constant-single-source` — route `CARD_PRESETS.planner.height` to `CARD_GRID.HEIGHT`, resolving the 150/160 drift; must precede the skeleton rows that consume the reconciled constant. (3) `grid-skeleton-count-from-pagesize` + (4) `deck-fallback-uses-card-dimensions` — the per-section fidelity fixes reading the reconciled constants. (5) `plan-detail-shell-first` — the deferred-render restructure, last, since it composes with phase-07's lazy editor and the reconciled skeletons. Rows 2–4 share `lib/constants.ts` + the skeleton components.
SLOT batch-boundary: `router-preload-intent` is a standalone cycle (isolated file). The constant reconciliation lands green before the two skeleton rows that consume it. `grid-skeleton-count-from-pagesize` + `deck-fallback-uses-card-dimensions` form one red batch (both skeleton-geometry assertions sharing the constants fixture). `plan-detail-shell-first` is its own cycle (structural, depends on phase 07). Sequence: preload | constant → skeleton batch | shell-first.
SLOT fallback-scope: No stub surface. If stopped, land in priority: constant reconciliation (unblocks fidelity) → `router-preload-intent` (cheap win) → skeleton fidelity → shell-first render last (largest change, composes with 07). The live geometry drill + CLS (phase 09, INV9/INV10) gates the fidelity outcome regardless.

# Phase 02 (local-tdd)

## Rows
- eager-set-within-budget: scripts/perf/eager-gate.mjs — eager-gate maps each chunk to its sourcemap sources and sums gzip
- shared-no-pages-import: frontend/.oxlintrc.json:no-restricted-imports — oxlint runs
- sse-single-auth-gated-subscription: frontend/src/shared/sse/__tests__/useAppSse.test.ts#opens-one-auth-gated-subscription-validated-by-schema — the app mounts and useAppSse runs at its new @/shared/sse path
- sync-dialog-first-login: frontend/src/shared/userSettings/__tests__/SyncChoiceDialog.test.tsx#opens-on-first-login-when-flag-set — the lazy dialog resolves and mounts
- settings-query-nonblocking: frontend/src/shared/userSettings/__tests__/useUserSettings.test.ts#fetches-non-blocking-for-authed-users — the query runs at its new @/shared/userSettings path
- gesellschaft-loader-prefetch-contract: frontend/src/lib/__tests__/router.test.tsx#gesellschaft-detail-loader-prefetches-caches-and-titles — the loader runs

## Touches
- frontend/src/shared/sse/**
- frontend/src/shared/userSettings/**
- frontend/src/components/layout/GlobalLayout.tsx
- frontend/src/lib/router.tsx
- frontend/src/pages/planner/index.ts
- frontend/src/pages/settings/index.ts
- frontend/src/pages/planner/hooks/useAppSse.ts
- frontend/src/pages/settings/**

## Validation
- `yarn --cwd frontend vitest run src/shared/sse src/shared/userSettings src/lib`

## Seams (drives)
- SyncChoiceDialog (lazy) -> opens for first-login users when the store flag is set
- eager-gate.mjs(dist) -> exit 0
- gesellschaft-detail loader -> blocks nav on prefetch, caches under publishedPlannerQueryKeys.detail(id) staleTime 5min, yields loaderData.title
- shared/* -> no import of @/pages/*
- useAppSse() -> single app-wide SSE subscription, events validated by PlannerSseEventSchema
- useUserSettingsQuery() -> non-blocking settings fetch for authed users

## DECISION SLOTS
SLOT ordering-rationale: Brownfield move (§7). The four characterization rows (sse, sync-dialog, settings, gesellschaft-loader) must be GREEN before the move — relocate them via `git mv` and confirm green at the new shared paths as the safety net, THEN drop the barrel exports, repoint consumers, lazy-wrap the dialog, and dynamic-import the loader. `eager-set-within-budget` flips red→green only after pages/* leaves the eager graph, so it is the terminal assertion. Do the sse move (shared/sse) and the settings move (shared/userSettings) as separate git-mv units, re-running each one's characterization test after.
SLOT batch-boundary: The four characterization rows are must-stay-green — the net, not a red batch. The single genuine red→green is `eager-set-within-budget` (red on the current build, green after de-pollution), verified by `scripts/perf/eager-gate.mjs` (phase-01 dep) at phase close. `shared-no-pages-import` (oxlint) stays green throughout. Sequence: net green → sse move → settings move → single eager-gate flip.
SLOT fallback-scope: stubAllow is `**/shared/sse/**` + `**/shared/userSettings/**`. If stopped mid-move, land the new shared public-API surfaces there as stubs re-exporting the still-in-slice implementations, so consumers can repoint to the final import path first; complete the `git mv` of implementations after. Never leave a barrel that both exports and re-exports the same symbol.

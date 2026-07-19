# Task: Frontend Performance Remediation + Measurement Harness (Lighthouse audit 2026-07-18)

Archives the design debate of 2026-07-18 (a 48-run Lighthouse sweep across all 24 routes plus Playwright profiling, geometry, INP, throttled/KR, and a CORS-corrected re-sweep) and the 2026-07-19 decision to productionize the measurement harnesses as committed tooling (R8). Two user rulings are load-bearing: all remediation proceeds as one task, and **no font binary may be converted** (creator licenses prohibit it).

Implementation-grade mechanics live in `mechanics.md` — read it before building any phase; it carries the measured baselines (the regression anchor), the eager-chunk offender inventory, the per-section skeleton geometry audit, the numeric budgets, and the harness recipe. The enforced contract rows live in `tests.manifest.json`.

N/A declarations: `docs/spec.md` §§1–4 (Data Model Catalog, Normalization Layer, Rendering Mode Enumeration, Reference Per Mode) do not apply — this task consumes no raw game-data enum fields; it changes module-graph shape, asset delivery, and adds tooling. `docs/spec.md` §5 (pipeline → schema → rendering ordering) applies and is honored (D8). The metered-external-service invariant is N/A — no metered sink (local Lighthouse; Cloudflare Pages static hosting).

## Decisions

- D1: Fix payload, not compute — TBT ≈ 0ms on all 48 runs and editor INP p95 = 32ms, while every mobile score is payload-capped; no memoization/virtualization is in scope (evidence: baselines, `mechanics.md` §1, §1c).
- D2: De-pollute the entry graph by moving shell-consumed modules into shared space (`@/shared/sse`, `@/shared/userSettings`) — `components/` is a dependency sink that must not import `@/pages/*`, and a module consumed by both the shell and a slice is multi-domain, which the layer rules already route to `shared/` (evidence: frontend/CLAUDE.md layer boundaries; eager inventory `mechanics.md` §2).
- D3 (default): Relocations use `git mv` + import-only edits, never agent rewrites (evidence: meme `decision-file-moves-within-page-slice-migrations`).
- D4: The gesellschaft-detail loader keeps its prefetch contract but obtains `fetchPublishedPlanner` via dynamic `import()` inside the loader body — it is the entry graph's only remaining planner-slice consumer; code-split at the data seam (evidence: `src/lib/router.tsx`).
- D5: Pretendard switches from the hand-written full-font `@font-face` to the vendored `pretendard-subset.css` (unicode-range slices) — user chose static subset over the variable dynamic-subset variant (evidence: user answer 2026-07-18; vendored file present).
- D6: S-Core Dream and KOTRA font binaries are untouchable — their creators' licenses prohibit conversion/binary-subsetting, so only CSS-side delivery scoping is permissible, and that is deferred (evidence: user-stated license restriction, 2026-07-18; meme `gotcha-s-core-dream-and-kotra-bold`).
- D7: Grid/browse surfaces render generated thumbnails; detail surfaces keep original art — grids render ~100px slots from 100–260KB originals (evidence: image attribution, `mechanics.md` §3).
- D8 (default): Static pipeline emits thumbnails before any frontend consumption — docs/spec.md §5 ordering, pipeline → rendering, never interleaved.
- D9: Async home/gesellschaft sections reserve layout space with skeletons — footer reflow under late sections is the CLS budget on home (0.38, confirmed live; the gesellschaft 0.63 first attributed here proved a CORS-failure artifact) (evidence: layout-shifts audit + live re-sweep, `mechanics.md` §1).
- D10: The tiptap note editor mounts via `React.lazy` behind the edit interaction — 105KB of the editor chunk is measured unused on display-only routes (evidence: unused-javascript audit).
- D11: The router enables `defaultPreload: 'intent'` — the chunk+loader window (~100ms unthrottled) sits serially before every SPA navigation; loaders are cache-idempotent (query-cache staleTime), so hover-triggered runs are safe (evidence: plan-loading profile, `mechanics.md` §1c).
- D12: Plan detail pages render shell-first — header from loader data first, heavy sections (deck, floors, notes) mount deferred — because all code and data are present by ~180ms while content appears at ~450ms; the ~270ms render tail (60%) is the dominant term. The loader-parallel-data lever was REJECTED by this profile: data already loads during routing (evidence: `mechanics.md` §1c).
- D13: Skeleton fidelity is a contract, not a convention — every placeholder reserves geometry derived from the same constants/counts as its content — because fidelity decays wherever hand-rolled: the plan viewer's per-section fallbacks are faithful in 2 of 7 sections and inconsistent in 5, the list grid skeleton renders 8 cards where pages load `PLANNER_LIST.PAGE_SIZE` (20), home's fallbacks are a 256px `LoadingState` against 2,116px, and the planner card constant (160px) has drifted from the rendered card (150px) (evidence: per-section audit, `mechanics.md` §1c; corrected 2026-07-18 after user review — an earlier draft misattributed the gap to the route-level pending fallback).
- D14 (default): The measurement harnesses live in `scripts/perf/`, mirroring the `scripts/ops/` suite convention (shell/node scripts + shared `lib/`), consistent with existing `scripts/load-test-*.js` (evidence: repo layout).
- D15 (taste): Heavy measurement/dev tooling keeps its dependencies isolated from the application's build dependency graph — `scripts/perf/` gets its own `package.json` and `node_modules` rather than adding lighthouse/playwright to `frontend/package.json` — because a tool the shipped app never imports should not inflate every app install and CI run.
- D16: The harness runs on demand and from `/build` verification, not in CI — full-stack Lighthouse in CI requires the whole backend+CORS stack and is flake-prone; a CI perf gate is deferred (evidence: CORS/stack dependency proven this session; `mechanics.md` §6).

## Requirements

- R1: Entry-graph de-pollution — move `useAppSse` (+ `PlannerSseEventSchema`) to `@/shared/sse`; move `SyncChoiceDialog`, `useFirstLoginStore`, `useUserSettingsQuery` to `@/shared/userSettings`; `GlobalLayout` imports only shared/components/lib; `SyncChoiceDialog` mounts via `React.lazy`; gesellschaft-detail loader uses dynamic import (D4). Planner/settings barrels drop the moved exports; consumers repoint after a reverse-import sweep.
- R2: Pretendard subset — `globals.css` references the vendored `pretendard-subset.css` instead of the full `Pretendard-Regular.woff2`; no other font file or declaration changes (D6).
- R3: Thumbnail pipeline — the static repo's image pipeline emits webp thumbnails for grid-consumed art (identity, ego, abEvent, themePack); `@/shared/assets` gains a thumb-variant accessor; grid cards consume thumbnails; detail pages keep originals.
- R4: Lazy image attributes — `AbEventCard` and `ThemePackCard` images gain `loading="lazy"` + `decoding="async"` (parity with IdentityCard/EGOCard/EGOGiftCard).
- R5: Layout reservation — home and gesellschaft async sections render fixed-height skeletons while loading; unsized header/footer icons get explicit dimensions.
- R6: Lazy note editor — `gesellschaft-detail` and `planner-md-new` load the tiptap chunk only when a note enters edit mode; read-only display does not pull prosemirror.
- R7: Plan-route latency + skeleton fidelity — (a) `defaultPreload: 'intent'` on the router (D11); (b) plan detail pages render shell-first with deferred heavy sections (D12); (c) the plan viewer's 5 inconsistent per-section fallbacks are reconciled to their content geometry (deck tiles use real card dimensions; Start Buff/Gift stubs match section heights; Comprehensive Gift Grid and Floor Theme Gallery get shaped skeletons replacing text-line fallbacks — the faithful observation/skill-replacement fallbacks are the model), `PlannerGridSkeleton` count derives from `PLANNER_LIST.PAGE_SIZE`, home's data sections get shaped skeletons, and the planner card constant is reconciled with the rendered card (D13).
- R8: Measurement harness tooling — the session's scratchpad harnesses become a committed `scripts/perf/` suite: the Lighthouse route sweep, chunk/eager attribution, Playwright plan-route profile + geometry + INP, and CDP-throttled runs, over a shared `lib/`, with a pinned baselines file (the archived numbers) and a self-test that reproduces them within tolerance. Self-contained `scripts/perf/package.json` (D15); runnable on demand and by `/build` verification (D16).

## Invariants

- INV1: No static import chain from the entry (`main.tsx` → router/GlobalLayout module scope) reaches `@/pages/*` — verify: eager-set gate (mechanics §6 step 5) shows no `pages/*`, `pako`, `dompurify`, `@tiptap/*` in entry + modulepreload.
- INV2: `shared/*` never imports `@/pages/*` — verify: oxlint `no-restricted-imports` stays green.
- INV3: Eager JS (entry + all modulepreload chunks) ≤ 320KB gzip — verify: eager-set gate (mechanics §5–6).
- INV4: Every file under `frontend/src/assets/fonts/` is byte-identical before/after — no conversion, re-encoding, or binary subsetting; only CSS references change — verify: git-diff gate on `assets/fonts/` at review.
- INV5: SSE behavior unchanged — one auth-gated app-wide subscription, events validated by `PlannerSseEventSchema` — verify: relocated `useAppSse` characterization (B1).
- INV6: gesellschaft-detail loader still blocks navigation on the prefetch, caches under `publishedPlannerQueryKeys.detail(id)` with staleTime 5min, yields `loaderData.title` — verify: loader test (B4).
- INV7: Thumbnails are ≥ 2× the largest rendered slot dimension of their consuming grid — verify: pipeline dimension assertion + one-page visual drill.
- INV8: Mobile Lighthouse budgets hold — FCP < 4s on all measured routes; LCP < 5s on browse routes (incl. gesellschaft-detail); CLS < 0.1 on home and gesellschaft — verify: harness re-run vs baseline (mechanics §1, §6).
- INV9: Every plan-route and home-section placeholder derives geometry from the same constants/counts as its content — grid rows = `ceil(PAGE_SIZE / columns)`, card dims from `CARD_GRID`, viewer fallbacks match their sections — verify: geometry drill (mechanics §6 step 7) + CLS (INV8).
- INV10: Deferred plan-detail sections cause no footer-visible reflow — each deferred section's skeleton reserves its chrome height before content mounts — verify: geometry drill + CLS on the detail routes.
- INV11: The committed harness, run per mechanics §6/§9 against the current build, reproduces the archived baselines within tolerance (Lighthouse ±1 mobile score, ±0.5s FCP; plan-route content p50 ±15%) — verify: harness self-test (R8).
- INV12: R8 adds no dependency to `frontend/package.json` or the app build — harness deps are isolated in `scripts/perf/package.json` — verify: git-diff gate on `frontend/package.json`.

## Behavior Inventory

| # | Seam | Observable behavior (as-is) | Verdict |
|---|------|-----------------------------|---------|
| B1 | GlobalLayout / `useAppSse` | Authed sessions open one app-wide SSE subscription; events validated by `PlannerSseEventSchema` | preserved |
| B2 | GlobalLayout / `SyncChoiceDialog` | First-login users get the sync-choice dialog when the store flag is set | preserved (loads on demand; may mount marginally later — D2) |
| B3 | GlobalLayout / `useUserSettingsQuery` | User settings fetched non-blocking at app load for authed users | preserved |
| B4 | Router / gesellschaft-detail loader | Navigation blocks on planner fetch; cache key `publishedPlannerQueryKeys.detail(id)`; staleTime 5min; title into head | preserved |
| B5 | `@/pages/planner` barrel | Exports `useAppSse`, `PlannerSseEventSchema` | dropped — moved to `@/shared/sse` (D2); consumers repointed same commit |
| B6 | `@/pages/settings` barrel | Exports `SyncChoiceDialog`, `useFirstLoginStore`, `useUserSettingsQuery` | dropped — moved to `@/shared/userSettings` (D2) |
| B7 | `AbEventCard` / `ThemePackCard` | All card images fetch eagerly at page load | dropped — lazy fetch (R4) |
| B8 | Browse-grid art | Grids render original full-resolution art | dropped — thumbnails at ≥ 2× slot (D7, INV7) |
| B9 | Detail-page art | Detail pages render originals | preserved |
| B10 | Note editor | tiptap loads with the route chunk; editor mounts synchronously | dropped — lazy chunk behind edit interaction; brief editor-loading state (D10) |
| B11 | Home/gesellschaft async sections | Sections render nothing until data, then push the footer down | dropped — skeleton reserves space (D9) |
| B12 | Body font rendering | All Pretendard glyphs render from one full font file | preserved (identical rendering; delivery sliced by unicode-range) |
| B13 | Router link hover | No network activity on hover | dropped — intent preload fetches chunk + runs loader on hover; loaders cache-idempotent (D11) |
| B14 | Plan detail initial commit | Entire plan tree renders in one commit | dropped — header + skeletons first, heavy sections deferred (D12) |
| B15 | Plan-viewer per-section fallbacks | 7 `Suspense` fallbacks; 2 faithful, 5 inconsistent (under-scaled deck tiles, 128px stubs for 200–320px sections, 2 text-line fallbacks); 8-card list grid; `LoadingState` on home sections | dropped — all reconciled to measured content geometry (D13) |

## Done When

- [ ] Eager-set gate passes: entry + modulepreload contain no `pages/*`, `pako`, `dompurify`, `@tiptap/*`; total ≤ 320KB gzip (live-only) — R1, INV3
- [ ] Relocated modules' tests pass at their new shared paths; full FE suite green: `yarn --cwd frontend vitest run` (local-tdd) — R1
- [ ] `AbEventCard` and `ThemePackCard` render `loading="lazy"` + `decoding="async"` (local-tdd) — R4
- [ ] Thumbnail pipeline emits variants for all grid-consumed art dirs; `@/shared/assets` resolves them; grids consume them (infra) — R3
- [ ] `globals.css` references `pretendard-subset.css`; the full-font Pretendard `@font-face` is gone; `assets/fonts/` byte-unchanged (local-tdd + gate) — R2, INV4
- [ ] Router has `defaultPreload: 'intent'`; hovering a list link prefetches the detail chunk + loader (local-tdd config; live network assertion) — R7
- [ ] Plan detail paints header + skeletons before heavy sections; the 5 inconsistent viewer fallbacks are reconciled; `PlannerGridSkeleton` count derives from `PLANNER_LIST.PAGE_SIZE`; planner card constant matches rendered height (local-tdd) — R7
- [ ] Geometry drill: skeleton vs loaded container heights within tolerance on gesellschaft list, both plan details, and home; plan-route content p50 improves vs mechanics §1b (live-only) — R7, INV9, INV10
- [ ] `scripts/perf/` suite runs the sweep, attribution, profile, geometry, and INP harnesses against a local preview and emits the archived metrics (infra) — R8
- [ ] Harness self-test reproduces the archived baseline within tolerance; `frontend/package.json` unchanged (live-only + gate) — R8, INV11, INV12
- [ ] Harness re-run meets INV8 budgets and non-planner mobile font transfer ≤ 200KB (live-only) — INV8
- [ ] All existing tests pass; oxlint + `tsc -b` green (local-tdd)

## Deferred

- CSS-side download scoping for S-Core Dream/KOTRA — deferred (binaries untouchable per D6); until then planner pages carry +306KB and all pages +57KB of font transfer.
- Latin-slice preload for the body font — deferred; until then cold loads may briefly render the fallback before the swap.
- The eager `date-fns`/`tailwind-merge` chunk (~60KB gz) — outside the eight fixes; stays within the 320KB budget until separately revisited.
- CI perf workflow — deferred (D16); until then budget regressions are caught only when the harness is run manually or by `/build`.
- Field data (CrUX / PSI) — blocked this session by keyless quota (429); a PSI API key unblocks it. Until then all numbers are lab.

## Test Plan

### Runner
- Frontend: vitest — `yarn --cwd frontend vitest run` (scoped during a phase, e.g. `yarn --cwd frontend vitest run src/shared/sse`). oxlint (`yarn --cwd frontend lint`), `tsc -b` (`yarn --cwd frontend typecheck`).
- Static pipeline: the pipeline's own `validation` step (`static/scripts/pipeline.py`) extended with the thumbnail dimension assertion (no separate test framework in the static repo).
- Harness self-test: `node scripts/perf/selftest.mjs` (or the suite's documented entry) against a running `vite preview` + backend.
- ArchUnit method-naming rule does not apply — this task's tests are vitest/pipeline, not backend JUnit.

### Task-level requirements
- INV3, INV8, INV11 — verified at task tier by the R8 harness against a full build, after all remediation phases land (see `tests.manifest.json` `taskLevel`).

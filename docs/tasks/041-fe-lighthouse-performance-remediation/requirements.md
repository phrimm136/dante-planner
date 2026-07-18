# Task: Frontend Performance Remediation (Lighthouse audit 2026-07-18)

Archives the design debate of the 2026-07-18 session: a 48-run Lighthouse sweep (24 routes × mobile/desktop, production build via `vite preview`) diagnosed the slowness factors, and the six-fix remediation below was agreed with the user, including two explicit user rulings — all fixes proceed through this spec as one task, and **no font binary may be converted** (creator licenses prohibit it). Implementation-grade mechanics live in `mechanics.md` — read it before building any phase; it carries the measured baseline, the eager-chunk inventory, the numeric budgets, and the verification-harness recipe.

N/A declarations: `docs/spec.md` sections 1-4 (Data Model Catalog, Normalization Layer, Rendering Mode Enumeration, Reference Per Mode) do not apply — this task consumes no raw game-data fields; it changes module-graph shape and asset delivery only. Section 5's ordering principle applies and is honored (Decision 8). The metered-external-service invariant is N/A — no metered sink is involved (local Lighthouse; Cloudflare Pages static hosting).

## Decisions

- Decision 1: Fix payload, not compute — because TBT ≈ 0ms on all 48 runs while every mobile score was payload-capped; no memoization/virtualization work is in scope (evidence: baseline table, `mechanics.md` §1).
- Decision 2: De-pollute the entry graph by moving shell-consumed modules into shared space (`@/shared/sse`, `@/shared/userSettings`) — because `components/` is a dependency sink that must not import `@/pages/*`, and a module consumed by both the shell and a slice is by definition multi-domain, which the layer rules already route to `shared/` (evidence: frontend/CLAUDE.md layer boundaries; eager-graph inventory `mechanics.md` §2).
- Decision 3 (default): Relocations use `git mv` plus import-only edits, never agent rewrites (evidence: prior decision fact `LimbusPlanner/decision-file-moves-within-page-slice-migrations`).
- Decision 4: The gesellschaft-detail route loader keeps its prefetch contract but obtains `fetchPublishedPlanner` via dynamic `import()` inside the loader body — because the loader is the entry graph's only remaining planner-slice consumer; code-split at the data seam (evidence: `src/lib/router.tsx:18-21,179-190`).
- Decision 5: Pretendard switches from the hand-written full-font `@font-face` to the vendored `pretendard-subset.css` (unicode-range slices) — user chose static subset over the variable dynamic-subset variant because effective weight usage is narrow (evidence: user answer 2026-07-18; vendored file at `frontend/src/assets/fonts/Pretendard-1.3.9/web/static/pretendard-subset.css`).
- Decision 6: S-Core Dream and KOTRA font binaries are untouchable — their creators' licenses prohibit conversion/modification, so no woff2 conversion and no binary subsetting; only CSS-side delivery scoping is permissible, and that is deferred (evidence: user-stated license restriction, 2026-07-18).
- Decision 7: Grid/browse surfaces render generated thumbnails; detail surfaces keep original art — because grids render ~100px slots from 100-240KB originals, and detail pages are the only surface where full-resolution art is user-visible value (evidence: image attribution, `mechanics.md` §3).
- Decision 8 (default): Static pipeline emits thumbnails before any frontend consumption lands — docs/spec.md §5 ordering: pipeline → schema → rendering, never interleaved.
- Decision 9: Async home/gesellschaft sections reserve their layout space with skeletons — because footer reflow under late-arriving sections is the entire CLS budget on home (0.38, confirmed with live data; the gesellschaft 0.63 initially attributed here proved to be a CORS-failure artifact of the first sweep) (evidence: layout-shifts audit + live-data re-sweep, `mechanics.md` §1).
- Decision 10: The tiptap note editor mounts via `React.lazy` behind the edit interaction — because 105KB of the editor chunk is measured unused on routes that merely display notes (evidence: unused-javascript audit).
- Decision 11: The router enables `defaultPreload: 'intent'` — because the chunk+loader window (~100ms unthrottled) sits serially before every SPA navigation and hover-preloading removes it; loaders are cache-idempotent (query-cache prefetch with staleTime), so hover-triggered loader runs are safe (evidence: plan-loading profile, `mechanics.md` §1c).
- Decision 12: The plan detail pages render shell-first: header + section skeletons paint immediately, heavy sections (deck grid, floors, notes) mount deferred — because the profile shows all code and data present by ~180ms while content appears at ~450ms; the ~270ms render tail (60% of total) is the dominant latency term, and no data-parallelization lever can touch it (evidence: `mechanics.md` §1c). The originally proposed loader-parallel data read was REJECTED by this profile — data already loads early via the route loader.
- Decision 13: Skeleton fidelity becomes a contract, not a convention — every placeholder must reserve geometry derived from the same constants and counts as the content that replaces it — because the audit found fidelity decays wherever it is hand-rolled: the plan viewer's per-section fallbacks are faithful in two sections but inconsistent in five (deck tiles at a fraction of real card size, 128px stubs for 200-320px sections, and two bare text-line fallbacks against 571px and ~2,000px of content), the list grid skeleton renders 8 cards where pages load `PLANNER_LIST.PAGE_SIZE` (20), home's Suspense fallbacks are a 256px `LoadingState` text box against 2,116px of content, and the planner card constant (160px) has drifted 10px from the rendered card (150px) (evidence: per-section geometry audit, `mechanics.md` §1c; corrected 2026-07-18 after user review — an earlier draft misattributed the gap to the route-level pending fallback).

## Description

Six requirement groups:

- **R1 Entry-graph de-pollution.** Move `useAppSse` (with `PlannerSseEventSchema`) to `@/shared/sse`; move `SyncChoiceDialog`, `useFirstLoginStore`, `useUserSettingsQuery` to `@/shared/userSettings`; `GlobalLayout` imports only shared/components/lib modules; `SyncChoiceDialog` mounts via `React.lazy`; gesellschaft-detail loader uses dynamic import (Decision 4). Barrels of the planner and settings slices drop the moved exports; all consumers repoint (reverse-import sweep first).
- **R2 Pretendard subset.** `globals.css` references the vendored `pretendard-subset.css` instead of declaring the full `Pretendard-Regular.woff2`. No other font file or declaration changes (Decision 6).
- **R3 Thumbnail pipeline.** The static repo's image pipeline gains a resize step emitting webp thumbnails for grid-consumed art (identity, ego, egoGift, themePack, abEvent card art). Frontend `@/shared/assets` helpers gain a thumb-variant accessor; grid cards consume thumbnails; detail pages keep originals.
- **R4 Lazy image attributes.** `AbEventCard` and `ThemePackCard` images gain `loading="lazy"` and `decoding="async"` (parity with IdentityCard/EGOCard/EGOGiftCard).
- **R5 Layout reservation.** Home and gesellschaft async sections render fixed-height skeletons while loading; the unsized header/footer icons get explicit dimensions.
- **R6 Lazy note editor.** `gesellschaft-detail` and `planner-md-new` load the tiptap editor chunk only when a note enters edit mode; read-only note display does not pull prosemirror.
- **R7 Plan-route latency + skeleton fidelity.** (a) `defaultPreload: 'intent'` on the router (Decision 11). (b) Plan detail pages (`/planner/md/$id`, gesellschaft detail) render shell-first with deferred heavy sections (Decision 12). (c) Skeleton fidelity: the plan viewer's inconsistent per-section fallbacks are reconciled to their content's geometry (deck tiles use real card dimensions from constants; Start Buff/Gift stubs match their section heights; Comprehensive Gift Grid and Floor Theme Gallery get shaped skeletons replacing their text-line fallbacks — the faithful observation/skill-replacement fallbacks are the pattern to copy); the route-level pending fallback aligns with the viewer's section-stack chrome; `PlannerGridSkeleton` count comes from `PLANNER_LIST.PAGE_SIZE`; home's data sections get shaped skeletons instead of `LoadingState` fallbacks; the planner card height constant is reconciled with the rendered card (Decision 13).

## Scope

READ: `frontend/src/components/layout/GlobalLayout.tsx`, `frontend/src/lib/router.tsx`, `frontend/src/pages/planner/{index.ts,hooks/useAppSse.ts,hooks/usePublishedPlannerQuery.ts,schemas/PlannerSchemas.ts}`, `frontend/src/pages/settings/{index.ts,components/SyncChoiceDialog.tsx,stores/useFirstLoginStore.ts,hooks/useUserSettings.ts}`, `frontend/src/styles/globals.css`, `frontend/src/styles/korean-font-coverage.css`, `frontend/src/assets/fonts/Pretendard-1.3.9/web/static/pretendard-subset.css`, `frontend/src/pages/{abEvent,themePack,home,egoGift}/components/`, `frontend/src/shared/assets/`, `frontend/.oxlintrc.json`, `static/CLAUDE.md`, `static/scripts/`, `frontend/vite.config.ts`, `frontend/vite-plugin-hash-static.ts`

## Target

CREATE: `frontend/src/shared/sse/` (module + public API), `frontend/src/shared/userSettings/` (module + public API), static-pipeline thumbnail step + emitted `static/images/**` thumb variants, characterization/regression tests listed in the Test Plan.
MODIFY: `GlobalLayout.tsx`, `router.tsx`, planner + settings barrels and their intra-slice consumers, `globals.css`, `@/shared/assets` helpers, `AbEventCard.tsx`, `ThemePackCard.tsx`, grid card components consuming thumbnails, home/gesellschaft async section components, note-editor mount points on `gesellschaft-detail` and `planner-md-new`.

## Invariants

- INV1: No static import chain from the entry (`main.tsx` → router/GlobalLayout module scope) reaches `@/pages/*` — verify: eager-set gate (mechanics §6) shows no `pages/*`, `pako`, `dompurify`, `@tiptap/*` in entry + modulepreload; oxlint boundary rules green.
- INV2: `shared/*` never imports `@/pages/*` (the closed boundary stays closed) — verify: existing oxlint `no-restricted-imports` stays green.
- INV3: Eager JS (entry + all modulepreload chunks) ≤ 320KB gzip — verify: bundle gate (mechanics §5-6).
- INV4: Every file under `frontend/src/assets/fonts/` is byte-identical before/after this task — no conversion, re-encoding, or binary subsetting of any font; only CSS references change — verify: `git status` clean under `assets/fonts/` at review.
- INV5: SSE behavior unchanged: one auth-gated app-wide subscription, events validated by `PlannerSseEventSchema` — verify: relocated `useAppSse` tests green (characterization, B1).
- INV6: gesellschaft-detail loader still blocks navigation on the prefetch, caches under `publishedPlannerQueryKeys.detail(id)` with `staleTime` 5min, and yields `loaderData.title` for the head — verify: loader test (B4).
- INV7: Thumbnails are ≥ 2× the largest rendered slot dimension of their consuming grid — verify: pipeline dimension assertion + one-page visual drill.
- INV8: Mobile Lighthouse budgets hold: FCP < 4s on all measured routes; LCP < 5s on browse routes; CLS < 0.1 on home and gesellschaft — verify: harness re-run vs baseline (mechanics §1, §6).
- INV9: Every plan-route and home-section placeholder derives its geometry from the same constants/counts as the content replacing it — grid skeleton rows = `ceil(PAGE_SIZE / columns)`, card dimensions from `CARD_GRID`, detail skeleton mirrors the viewer's section stack — verify: geometry drill (mechanics §6 step 7) + CLS budget (INV8).
- INV10: Deferred plan-detail sections cause no footer-visible reflow: each deferred section's skeleton reserves its chrome height before content mounts — verify: geometry drill + CLS on the detail routes.

## Behavior Inventory

| # | Seam | Observable behavior (as-is) | Verdict |
|---|------|-----------------------------|---------|
| B1 | GlobalLayout / `useAppSse` | Authed sessions open one app-wide SSE subscription; events validated by `PlannerSseEventSchema` | preserved |
| B2 | GlobalLayout / `SyncChoiceDialog` | First-login users get the sync-choice dialog when the store flag is set | preserved (dialog code loads on demand; may mount marginally later — Decision 2) |
| B3 | GlobalLayout / `useUserSettingsQuery` | User settings fetched non-blocking at app load for authed users | preserved |
| B4 | Router / gesellschaft-detail loader | Navigation blocks on planner fetch; cache key `publishedPlannerQueryKeys.detail(id)`; staleTime 5min; title into head | preserved |
| B5 | `@/pages/planner` barrel | Exports `useAppSse`, `PlannerSseEventSchema` | dropped — moved to `@/shared/sse` (Decision 2); consumers repointed in the same commit |
| B6 | `@/pages/settings` barrel | Exports `SyncChoiceDialog`, `useFirstLoginStore`, `useUserSettingsQuery` | dropped — moved to `@/shared/userSettings` (Decision 2) |
| B7 | `AbEventCard` / `ThemePackCard` | All card images fetch eagerly at page load | dropped — lazy fetch (R4) |
| B8 | Browse-grid art | Grids render original full-resolution art | dropped — thumbnails at ≥ 2× slot (Decision 7, INV7) |
| B9 | Detail-page art | Detail pages render originals | preserved |
| B10 | Note editor | tiptap loads with the route chunk; editor mounts synchronously | dropped — lazy chunk behind edit interaction; brief editor-loading state (Decision 10) |
| B11 | Home/gesellschaft async sections | Sections render nothing until data, then push the footer down | dropped — skeleton reserves space (Decision 9) |
| B12 | Body font rendering | All Pretendard glyphs render from one full font file | preserved (identical rendering; delivery sliced by unicode-range) |
| B13 | Router link hover | No network activity on hover | dropped — intent preload fetches route chunk + runs loader on hover; loaders are cache-idempotent (Decision 11) |
| B14 | Plan detail initial commit | Entire plan tree (header, deck, floors, notes) renders in one commit | dropped — header + skeletons first, heavy sections mount deferred (Decision 12) |
| B15 | Plan-route pending/loading placeholders | Per-section Suspense fallbacks exist; 5 of 7 are inconsistent with their content (under-scaled tiles, 128px stubs, two text-line fallbacks); 8-card grid skeleton; `LoadingState` text box on home sections | dropped — all placeholders reconciled to measured content geometry (Decision 13) |

## Done When

- [ ] Eager-set gate passes: entry + modulepreload contain no `pages/*`, `pako`, `dompurify`, `@tiptap/*`; total ≤ 320KB gzip (live-only)
- [ ] Relocated modules' tests pass at their new shared paths; full FE suite green: `yarn --cwd frontend vitest run` (local-tdd)
- [ ] `AbEventCard` and `ThemePackCard` render `loading="lazy"` + `decoding="async"` (local-tdd)
- [ ] Thumbnail pipeline emits variants for all grid-consumed art directories; `@/shared/assets` resolves them; grids consume them (local-tdd both repos)
- [ ] `globals.css` references `pretendard-subset.css`; the full-font Pretendard `@font-face` is gone (local-tdd)
- [ ] Harness re-run meets INV8 budgets and non-planner mobile font transfer ≤ 200KB (live-only)
- [ ] Router has `defaultPreload: 'intent'`; hovering a list link prefetches the detail chunk + loader (local-tdd for config; live-only for the network assertion)
- [ ] Plan detail routes paint header + skeletons before heavy sections; `DetailPageSkeleton` gains a `planner` preset; ad-hoc inline skeletons and home `LoadingState` fallbacks are replaced (local-tdd)
- [ ] `PlannerGridSkeleton` card count derives from `PLANNER_LIST.PAGE_SIZE`; planner card constant matches rendered card height (local-tdd)
- [ ] Geometry drill: skeleton vs loaded container heights within tolerance on gesellschaft list, both plan details, and home; plan-route content p50 improves vs mechanics §1b (live-only)
- [ ] All existing tests pass; oxlint + `tsc -b` green (local-tdd)

## Deferred

- CSS-side download scoping for S-Core Dream/KOTRA — deferred (feasibility unassessed; binaries untouchable per Decision 6); until then planner pages carry +306KB and all pages +57KB of font transfer.
- Latin-slice preload for the body font — deferred; until then cold loads may briefly render the fallback font before the swap.
- The eager `date-fns`/`tailwind-merge` chunk (~60KB gz) — outside the six agreed fixes; it stays inside the 320KB eager budget until separately revisited.
- `/planner/md/$id` and `/planner/md/$id/edit` remain outside the throttled Lighthouse baseline (IndexedDB-dependent) — a Playwright seeded-storage baseline now covers functional loading (`mechanics.md` §1b); the throttled-comparable gap persists.

## Test Plan

### Test Runner
- Frontend: vitest — `yarn --cwd frontend vitest run` (scoped: `yarn --cwd frontend vitest run src/shared/sse src/shared/userSettings` during R1)
- Static pipeline: the pipeline's own `validation` step (`scripts/pipeline.py`) extended with the thumbnail dimension assertion (default — no separate test framework exists in the static repo)

### Tests to Write
- [ ] B1 characterization: `useAppSse` subscribes once, auth-gated, schema-validates — `frontend/src/shared/sse/__tests__/useAppSse.test.ts` (moved with `git mv`, path-updated)
- [ ] B2: first-login flow still opens `SyncChoiceDialog` (lazy mount awaited) — `frontend/src/shared/userSettings/__tests__/SyncChoiceDialog.test.tsx`
- [ ] B3: settings query non-blocking behavior — relocated existing test
- [ ] B4/INV6: gesellschaft-detail loader prefetch contract — `frontend/src/lib/__tests__/router.test.tsx` (or existing router test file per convention)
- [ ] R4: card img attribute assertions — `frontend/src/pages/abEvent/components/__tests__/AbEventCard.test.tsx`, `frontend/src/pages/themePack/components/__tests__/ThemePackCard.test.tsx`
- [ ] R3: `@/shared/assets` thumb accessor resolution — existing assets helper test file
- [ ] INV7: pipeline dimension assertion in the static `validation` step
- [ ] Every invariant above has its verification realized; every preserved inventory row is pinned by a characterization test before its seam moves

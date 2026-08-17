# Task: Migrate app-shell & utility pages into page slices (home, settings, keyword, moderator, legal) and relocate NotFoundPage

Continues the `26-fe-entities-migration` / `27-fe-planner-migration` series: dissolve the split-brain where a route component lives in `src/routes/` while its parts live in `src/components/<name>/`. Move the remaining routed pages into the flat page-slice model under `src/pages/`, leaving genuinely shared infrastructure shared.

## Decisions

- **Move in 3 buckets, not 1 uniform sweep** — because the 7 candidates are 3 different categories, and the page-slice model was designed around game-data nouns; applying full-slice ceremony uniformly creates dead boundaries and invites bad cross-edges. (evidence: brainstorm consensus; `arch-fe-architecture-decided-implemented-2026-06`)
  - **Domain slice**: `keyword` → `pages/keyword/`
  - **Feature slices**: `home`, `settings`, `moderator` → `pages/<name>/`
  - **Trivial**: `privacy` + `terms` → `pages/legal/` (with extracted layout); `notfound` → `components/common/`
- **No new `index.ts` public API for any moved slice** — the public-API barrel exists solely as the eslint-allowed import surface for *external* consumers; the router deep-imports route components and is eslint-exempt. Grep proved nothing outside imports the moved code. (evidence: user answer "Only if consumed externally"; `invariant-fe-page-slice-boundaries-one-eslint`; consumer greps below)
- **`notfound` is a fallback component, not a page** — it is `defaultNotFoundComponent`, eagerly imported, and 2 of its 3 importers already live in `components/common/`. It belongs there beside `RouteErrorComponent`/`ErrorState`. (evidence: user answer; `routes/NotFoundPage` imported by `lib/router.tsx`, `components/common/RouteErrorComponent.tsx:7`, `components/common/ErrorBoundary.tsx:6`)
- **`privacy` + `terms` collapse into one `LegalPage` component** parameterized by `{ namespace, sections, bulletSections }` — they share an identical JSX skeleton, differing only in i18n namespace, the section-key array, and which sections render bullet lists. DRY (rule #6), not premature abstraction. (evidence: `routes/PrivacyPage.tsx` vs `routes/TermsPage.tsx` are line-for-line parallel)
- **Dual-consumer modules STAY shared** (the established planner-migration rule): a module consumed by the moved page *and* another domain is not pulled into the slice. (evidence: `invariant-planner-page-slice-migration-rule-commit`)
  - Keyword **formatter infra** (`FormattedDescription`, `FormattedKeyword`, `useKeywordFormatter`, `useBattleKeywords`, `lib/keywordFormatter`) — consumed by identity, ego, egoGift, planner, and `components/common/SkillDescription` (5 domains). Stays.
  - Keyword **data hooks** (`useKeywordListData`, `useKeywordDetailData`) — `useKeywordListData` is consumed by the shared `components/filter/BattleKeywordDropdown.tsx`. Keep the sibling pair together in `hooks/`. Stays.
  - `types/KeywordTypes.ts` — imported by the shared formatter infra. Stays.
  - `schemas/KeywordSchemas.ts` + `schemas/BattleKeywordsSchemas.ts` — live in the shared `schemas/index.ts` barrel (lines 22–31). Stays.
  - `components/moderation/BanStatusBanner.tsx` (used by GlobalLayout) + `hooks/useModeratorCommentDelete.ts` (used by planner's CommentSection) — falsely co-located, not moderator-page-owned. Stay untouched. (evidence: grep — both import only shared `useAuthQuery`/`ApiClient`/`linkifyText`, nothing that moves)
- **Slice directory names are singular nouns matching the route** — `pages/keyword/` (route `/keyword`, matches `docs/tasks/020-keyword` and existing `identity`/`ego` singular convention), `pages/moderator/`, `pages/home/`, `pages/settings/`, `pages/legal/`. (evidence: existing `pages/<noun>/` dirs are singular)
- **`routes/` is NOT deleted** — it also contains `api/` and `auth/` subtrees that are out of scope. Only the 8 page files (+ `routes/__tests__/HomePage.test.tsx`) leave it. (evidence: `ls routes/` → `api/`, `auth/`, `__tests__/` remain)
- **Route paths and i18n keys are unchanged** — this is a module-location refactor only; `/`, `/settings`, `/keyword`, `/keyword/$id`, `/moderation`, `/privacy`, `/terms`, and the 404 fallback keep their paths and `pages.*`/`header.*` keys. Footer links to `/privacy` `/terms` are unaffected.
- **`docs/spec.md` Data-Driven sections are N/A** — that template applies to features consuming raw game data files; this refactor moves UI modules and touches no data pipeline, schema generation, or rendering of raw data. Recorded here so the omission is deliberate, not an oversight.

## Resolved Ambiguities

| Question | Resolution | Source |
|----------|------------|--------|
| Does anything import *from* home/settings/moderator code? | No external consumers → no `index.ts` for those slices | Codebase (grep `@/components/{home,settings,moderation}` from outside = empty) |
| Does `keyword`'s owned code (components, detail hook) have external consumers? | `@/components/keyword` = no external importers; keyword components carry no public API | Codebase (grep empty) |
| Is `useKeywordListData` keyword-page-owned or shared? | Shared — `components/filter/BattleKeywordDropdown.tsx:36-37` consumes it; stays in `hooks/` | Codebase `components/filter/BattleKeywordDropdown.tsx:36` |
| Should `useKeywordDetailData` move (no external consumer) or stay with its sibling? | Stay in `hooks/` beside `useKeywordListData` — keep the pair together, more surgical | Default (dual-consumer convention + rule #1) |
| Where do `KeywordTypes` / `KeywordSchemas` / `BattleKeywordsSchemas` go? | Stay — types feed shared formatter infra; schemas are in the shared `schemas/index.ts` barrel | Codebase `schemas/index.ts:22-31`, `lib/keywordFormatter.ts:18` |
| Do the stay-put moderation files (`BanStatusBanner`, `useModeratorCommentDelete`) depend on anything that moves? | No — they import only `useAuthQuery`, `ApiClient`, `linkifyText` (all shared). Leave both in place | Codebase (import grep) |
| Do `ModeratorSchemas` / `ModeratorTypes` have external consumers? | None → move into `pages/moderator/` cleanly | Codebase (grep empty) |
| Are privacy/terms similar enough to extract a shared layout? | Yes — identical skeleton; extract `LegalPage({ namespace, sections, bulletSections })` | Codebase `routes/PrivacyPage.tsx` ≈ `routes/TermsPage.tsx` |
| Where does `NotFoundPage` go, and who must be updated? | `components/common/NotFoundPage.tsx`; update 3 importers (`router.tsx`, `RouteErrorComponent.tsx`, `ErrorBoundary.tsx`) | Codebase (`grep @/routes/NotFoundPage` → 3 hits) |
| Should the moved slices get an `index.ts`? | No — none has an external consumer; router is eslint-exempt | User ("Only if consumed externally") |
| Should privacy/terms/notfound be 3 slices or grouped? | `pages/legal/` for privacy+terms; `components/common/` for notfound | User ("Grouped + notfound→common") |
| Next spec dir number? | `28` — repo uses 2-digit numbering, highest is `27`; the `/spec` 3-digit scanner misfired | Codebase (`ls docs/`) |
| Does `docs/spec.md` add required sections here? | Data-Driven sections are N/A (no raw game-data consumption) | Convention (`docs/spec.md` scope) |
| Test runner? | Vitest; co-located `__tests__/` move with their subject files | Convention (`frontend/CLAUDE.md`; `feedback_tests_convention`) |
| Is `routes/` deleted afterward? | No — `api/` and `auth/` remain; only the 8 page files + `HomePage.test.tsx` leave | Codebase (`ls routes/`) |

## Description

Relocate eight route components and their page-owned dependencies out of `src/routes/` and `src/components/<name>/` into the page-slice model, update all import sites, and verify behavior is unchanged.

1. **`pages/home/`** — move `HomePage.tsx`, its 8 `components/home/*`, the home-owned `hooks/useHomePageData.ts`, and all co-located home tests. No `index.ts`. `useHomePageData` keeps importing `@/pages/identity` / `@/pages/ego` (public APIs, unchanged).
2. **`pages/settings/`** — move `SettingsPage.tsx` and its 7 `components/settings/*` + tests. No `index.ts`. `SettingsPage` keeps importing `PlannerExportImportSection` from `@/pages/planner` (public API, unchanged).
3. **`pages/keyword/`** — move `KeywordPage.tsx`, `KeywordDetailPage.tsx`, and the 4 `components/keyword/*`. Leave the keyword data hooks, formatter infra, types, and schemas in their shared locations. No `index.ts`. The keyword detail route loader stays inline in `router.tsx`.
4. **`pages/moderator/`** — move `ModeratorPage.tsx`, `components/moderation/BanDialog.tsx`, `hooks/useModerator{Data,Mutations}.ts`, `schemas/ModeratorSchemas.ts`, `types/ModeratorTypes.ts`. Leave `BanStatusBanner.tsx` (+ its test) and `useModeratorCommentDelete.ts` untouched. No `index.ts`.
5. **`pages/legal/`** — move `PrivacyPage.tsx` + `TermsPage.tsx`; extract `pages/legal/components/LegalPage.tsx` parameterized by `{ namespace, sections, bulletSections }`; both route files become thin wrappers. No `index.ts`.
6. **`components/common/NotFoundPage.tsx`** — move from `routes/`; update the 3 importers (`router.tsx` keeps the eager import at its new path; `RouteErrorComponent.tsx` and `ErrorBoundary.tsx` switch to `./NotFoundPage`).
7. **`router.tsx`** — repoint all 8 lazy/eager imports to the new module paths. Route paths, `head`/meta, and the keyword loader are unchanged.
8. **Tests** — every co-located `__tests__/*` moves with its subject; `routes/__tests__/HomePage.test.tsx` → `pages/home/__tests__/`. Update any relative/`@/` import inside moved tests.

## Scope

Files/folders to READ for context:
- `frontend/src/lib/router.tsx` — route registrations to repoint
- `frontend/eslint.config.js` — the `no-restricted-imports` `@/pages/*/**` rule + router exemption
- `frontend/src/pages/extraction/` and `frontend/src/pages/identity/` — slice shape reference (route-at-root + `components/` etc.)
- `frontend/src/components/common/RouteErrorComponent.tsx`, `ErrorBoundary.tsx` — notfound importers
- `frontend/src/components/Footer.tsx` — `/privacy` `/terms` links (must keep working)
- `frontend/src/schemas/index.ts` — confirms keyword schemas stay in the shared barrel

## Target

CREATE:
- `frontend/src/pages/home/` (`HomePage.tsx`, `components/`, `hooks/useHomePageData.ts`, `__tests__/`)
- `frontend/src/pages/settings/` (`SettingsPage.tsx`, `components/`)
- `frontend/src/pages/keyword/` (`KeywordPage.tsx`, `KeywordDetailPage.tsx`, `components/`)
- `frontend/src/pages/moderator/` (`ModeratorPage.tsx`, `components/BanDialog.tsx`, `hooks/`, `schemas/`, `types/`)
- `frontend/src/pages/legal/` (`PrivacyPage.tsx`, `TermsPage.tsx`, `components/LegalPage.tsx`)
- `frontend/src/components/common/NotFoundPage.tsx`

MODIFY:
- `frontend/src/lib/router.tsx` — 8 import paths
- `frontend/src/components/common/RouteErrorComponent.tsx`, `frontend/src/components/common/ErrorBoundary.tsx` — notfound import → `./NotFoundPage`
- Any moved test file whose relative imports change

DELETE (after move):
- `frontend/src/routes/{HomePage,SettingsPage,KeywordPage,KeywordDetailPage,ModeratorPage,PrivacyPage,TermsPage,NotFoundPage}.tsx`
- `frontend/src/routes/__tests__/HomePage.test.tsx` (and the now-empty `routes/__tests__/` if nothing else remains)
- `frontend/src/components/{home,settings,keyword}/` (emptied) and `components/moderation/BanDialog.tsx`
- `frontend/src/hooks/useHomePageData.ts`, `useModeratorData.ts`, `useModeratorMutations.ts`; `schemas/ModeratorSchemas.ts`; `types/ModeratorTypes.ts`

STAY UNTOUCHED (explicit non-targets):
- `frontend/src/routes/api/`, `frontend/src/routes/auth/`
- `frontend/src/hooks/useKeywordListData.ts`, `useKeywordDetailData.ts`, `useKeywordFormatter.ts`, `useBattleKeywords.ts`, `useModeratorCommentDelete.ts`
- `frontend/src/types/KeywordTypes.ts`; `frontend/src/schemas/KeywordSchemas.ts`, `BattleKeywordsSchemas.ts`; `frontend/src/lib/keywordFormatter.ts`
- `frontend/src/components/common/Formatted{Description,Keyword}.tsx`, `frontend/src/components/moderation/BanStatusBanner.tsx` (+ test), `frontend/src/components/filter/BattleKeywordDropdown.tsx`

## Impact Analysis

- **Files modified**: `router.tsx` (high — composition root, 8 edits), `RouteErrorComponent.tsx` / `ErrorBoundary.tsx` (low — 1 import each). All other changes are `git mv` + internal import-path rewrites.
- **Dependencies**:
  - `router.tsx` is the only consumer of all 8 route components (plus 2 extra notfound consumers).
  - `home` → `@/pages/identity`, `@/pages/ego` (public API, unchanged).
  - `settings` → `@/pages/planner` (public API, unchanged).
  - moved slices → shared `hooks/`, `lib/`, `types/`, `schemas/`, `components/common/`, `components/ui/` (all allowed, unchanged).
- **Ripple effects**: a missed import path → TypeScript compile error (caught by `tsc -b`). A moved-but-still-shared module would break its external consumer (prevented by the stay-untouched list). No route path changes → no nav/footer/i18n ripple.

## Risk Assessment

- **Edge cases**:
  - `NotFoundPage` is eagerly imported (`defaultNotFoundComponent`) — its new module must stay dependency-light so it never pulls weight into the entry chunk.
  - `routes/__tests__/` and `components/<name>/` directories left empty after moves should be removed to avoid dead dirs.
  - Moved test files may use relative imports (`../Foo`) that change depth under the new path.
  - `LegalPage` extraction must preserve the per-page `bulletSections` difference (privacy: `dataCollected`, `dataUse`; terms: `userConduct`) and the contact-section mailto link.
- **Performance**: neutral. Lazy code-splitting boundaries are unchanged (router still `lazyRouteComponent`s each route by its new path). Entry-bundle size unchanged provided notfound stays light.
- **Security**: none — no auth, validation, or trust boundary is altered. The moderator role gate and settings auth-conditional sections move verbatim.

## Boundaries & Invariants

- **Trust/ownership boundary**: none crossed — this is an internal module reorganization. The only "ownership" boundary is the page-slice import boundary enforced by eslint.
- **Invariant 1 (eslint boundary)**: no non-`router.tsx` file imports a deep `@/pages/*/**` path; cross-slice access is via public API only. After the move, **zero** new public APIs exist, so no new cross-slice import is even possible.
- **Invariant 2 (dual-consumer)**: every module consumed by ≥2 domains remains in shared space (`hooks/`, `lib/`, `types/`, `schemas/`, `components/common`, `components/filter`, `components/moderation/BanStatusBanner`). Asserted by the stay-untouched list.
- **Invariant 3 (bundle)**: Tiptap stays out of the entry chunk; the eagerly-imported `NotFoundPage` imports nothing heavy.
- **Invariant 4 (behavior preservation)**: each route renders identically and resolves at the same path; i18n keys, meta titles, and the keyword loader are byte-unchanged.
- **Invariant 5 (acyclic graph)**: the module-import graph remains a DAG — moved slices import shared + other slices' public APIs one-way; nothing imports back into them.

## Failure Modes

| Invariant | Trigger (how it breaks) | Response | Test |
|-----------|-------------------------|----------|------|
| Inv 4 (behavior) | Migration re-run / partial `git mv` lands twice (idempotency) | `git mv` of an already-moved file errors loudly; no silent double-state; final tree is path-deterministic | `tsc -b` + `vitest run` green |
| Inv 4 (behavior) | Process dies mid-move (partial state, atomicity) | Half-rewritten imports → `tsc -b` fails; illegal state is build-time only, never shipped; resumable by completing remaining moves | `yarn --cwd frontend tsc -b` exits non-zero on partial |
| Inv 2 (dual-consumer) | A shared module is pulled into a slice and its external consumer now deep-imports it (interleave of ownership) | Prevented at design time: only zero-external-consumer code moves; eslint flags any resulting `@/pages/*/**` deep import | `yarn --cwd frontend lint` green; `BattleKeywordDropdown`/`FormattedDescription` tests pass |
| Inv 1 (eslint) | A moved test/file keeps an old `@/routes/...` or gains a deep `@/pages/...` path | eslint `no-restricted-imports` errors; unresolved `@/routes/*` errors in `tsc` | `lint` + `tsc -b` green |
| Inv 3 (bundle) | `NotFoundPage`'s new location gains a heavy transitive import | Keep it importing only `Link`, i18n, `Button` (as today) | `yarn --cwd frontend build` succeeds; manual entry-chunk check |

### Visualized Failure
> Worst row: Inv 2 (dual-consumer) — the keyword-infra trap.

1. Implementer, "completing" the keyword slice, moves `useKeywordListData` (and/or `FormattedDescription`) into `pages/keyword/`.
2. `components/filter/BattleKeywordDropdown.tsx` (shared, rendered by multiple filter pages) now must import from `@/pages/keyword`.
3. To satisfy eslint it imports the slice public API — so a `pages/keyword/index.ts` gets created, re-exporting the hook, which transitively pulls keyword data/popover code.
4. **Broken state**: opening an Identity/EGO filter page now drags the entire keyword slice into that chunk; later keyword backlinks import `@/pages/identity`, forming an identity↔keyword cycle — the exact over-fit tier coupling the architecture already rejected.
5. → **Response (Inv 2 + stay-untouched list)** intervenes at step 1: `useKeywordListData` and the formatter infra are on the do-not-move list precisely because grep shows 5 external domains consume them. Keeping them shared makes steps 2–4 impossible.

## Done When

- [ ] `frontend/src/routes/` contains only `api/` and `auth/` (no `*Page.tsx`, no `__tests__/HomePage.test.tsx`)
- [ ] `pages/home`, `pages/settings`, `pages/keyword`, `pages/moderator`, `pages/legal` exist with route components at slice root and owned `components/`/`hooks/`/`schemas/`/`types/` as listed; **none has an `index.ts`**
- [ ] `components/common/NotFoundPage.tsx` exists; `router.tsx`, `RouteErrorComponent.tsx`, `ErrorBoundary.tsx` import it from the new path; no `@/routes/NotFoundPage` references remain
- [ ] `pages/legal/components/LegalPage.tsx` exists; `PrivacyPage`/`TermsPage` are thin wrappers; both pages render identical content to pre-refactor (sections, bullets, mailto)
- [ ] Stay-untouched list is byte-unchanged: keyword formatter infra, keyword data hooks, `KeywordTypes`, keyword schemas, `BanStatusBanner` (+ test), `useModeratorCommentDelete`, `BattleKeywordDropdown`
- [ ] All 8 route paths resolve unchanged: `/`, `/settings`, `/keyword`, `/keyword/$id`, `/moderation`, `/privacy`, `/terms`, and the 404 fallback
- [ ] `yarn --cwd frontend tsc -b` passes (redirect to `/tmp`)
- [ ] `yarn --cwd frontend lint` passes — no new `no-restricted-imports` violations, no orphaned `@/routes/*` imports
- [ ] `yarn --cwd frontend vitest run` passes — every moved test green in its new location
- [ ] All existing tests pass
- [ ] No empty directories left behind (`routes/__tests__`, `components/{home,settings,keyword}` removed if emptied)

## Test Plan

### Test Runner
- Framework: **Vitest** (+ React Testing Library), per `frontend/CLAUDE.md`
- Run commands (redirect output to `/tmp/<prefix>-<session-id>-<suffix>.log`):
  - `yarn --cwd frontend tsc -b`
  - `yarn --cwd frontend lint`
  - `yarn --cwd frontend vitest run`

### Tests to Write
- [ ] No *new* behavior tests required — this is a behavior-preserving move. Verification is: existing moved tests pass in their new locations.
- [ ] Realize each Failure-Modes `Test` cell: `tsc -b` (partial-state + Inv 1), `lint` (Inv 2 + Inv 1), `vitest run` (Inv 4 + dual-consumer consumer tests), `build` (Inv 3).
- [ ] Move + fix imports for: `routes/__tests__/HomePage.test.tsx`, `components/home/__tests__/*` (5), `components/settings/__tests__/*` (4). `components/moderation/__tests__/BanStatusBanner.test.tsx` and keyword/hook tests stay put.

## Verification

### Manual
1. `yarn --cwd frontend dev`; load `/` — banner, announcements, recent releases, community plans render.
2. Visit `/settings` (logged out then logged in) — auth-conditional sections + export/import render.
3. Visit `/keyword`, apply the keyword filter dropdown on a database list page (e.g. identity) — `BattleKeywordDropdown` still resolves; open `/keyword/$id` — backlinks render.
4. Visit `/moderation` as MODERATOR/ADMIN — user + history tables, ban/timeout dialogs work; confirm `BanStatusBanner` still shows for a banned user globally.
5. Visit `/privacy` and `/terms` via footer links — content identical to before; mailto link works.
6. Navigate to a bogus path — 404 fallback renders.

### Edge Cases
- [ ] Eager `NotFoundPage` import: entry chunk size unchanged (no heavy transitive pull)
- [ ] Moved test relative imports resolve at new depth
- [ ] `LegalPage` `bulletSections` differ correctly per page (privacy vs terms)
- [ ] No dead/empty directories remain under `routes/` or `components/`

# Execution Plan

## Phase Summary
Behavior-preserving file-move refactor, executed one bucket per phase, sequential (every phase edits the single composition root `frontend/src/lib/router.tsx`, so phases cannot run in parallel). Each phase: `git mv` the owned files, rewrite imports (intra-slice → relative, cross-slice → `@/pages/<other>` public API, shared → `@/...`), repoint `router.tsx`, remove emptied dirs, then verify `tsc -b` + `lint` + scoped `vitest`. Ordered simplest→riskiest so the pattern is proven before the dual-consumer-sensitive keyword move.

## Global Invariants (every phase must hold)
- Intra-slice imports are **relative** (`./components/X`), never `@/pages/<self>/**` (eslint bans it).
- The **stay-untouched list** (spec §Target) is never moved or edited: keyword formatter infra, keyword data hooks, `KeywordTypes`, keyword schemas, `BanStatusBanner` (+test), `useModeratorCommentDelete`, `BattleKeywordDropdown`, `routes/api`, `routes/auth`.
- No moved slice gets an `index.ts`.
- Route paths, `head`/meta, i18n keys, and the keyword loader are byte-unchanged.
- After each phase: `yarn --cwd frontend tsc -b` and `yarn --cwd frontend lint` pass; no `@/routes/<MovedPage>` reference remains.

## Phases

### Phase 1: NotFoundPage → components/common
- Files: `git mv routes/NotFoundPage.tsx → components/common/NotFoundPage.tsx`; edit importers `components/common/RouteErrorComponent.tsx` + `components/common/ErrorBoundary.tsx` (→ `./NotFoundPage`); edit `lib/router.tsx` line 15 (→ `@/components/common/NotFoundPage`).
- Tests: none (no existing test).
- Depends on: none.
- Verify: `tsc -b`, `lint`, `grep -r "@/routes/NotFoundPage" src` returns nothing; 404 fallback renders.

### Phase 2: legal slice (privacy + terms + LegalPage)
- Files: create `pages/legal/components/LegalPage.tsx` (extract shared skeleton, props `{ namespace, sections, bulletSections }`); `git mv routes/PrivacyPage.tsx → pages/legal/PrivacyPage.tsx` + refactor to thin wrapper; same for `TermsPage.tsx`; edit `router.tsx` (2 paths → `@/pages/legal/...`).
- Tests: none required; verify render parity (sections, bullets per page, mailto).
- Depends on: none.
- Verify: `tsc -b`, `lint`, `/privacy` + `/terms` render identical content to pre-refactor.

### Phase 3: home slice
- Files: `git mv routes/HomePage.tsx → pages/home/HomePage.tsx`; `git mv components/home/* → pages/home/components/*` (incl. `__tests__/`); `git mv hooks/useHomePageData.ts → pages/home/hooks/`; `git mv routes/__tests__/HomePage.test.tsx → pages/home/__tests__/`; rewrite imports; edit `router.tsx`; remove emptied `components/home/`, `routes/__tests__/` if empty.
- Tests: move + fix `HomePage.test.tsx` + 5 `components/home/__tests__/*`.
- Depends on: none (uses `@/pages/identity`, `@/pages/ego` public APIs — unchanged).
- Verify: `tsc -b`, `lint`, `vitest run` for home tests green.

### Phase 4: settings slice
- Files: `git mv routes/SettingsPage.tsx → pages/settings/SettingsPage.tsx`; `git mv components/settings/* → pages/settings/components/*` (incl. `__tests__/`); rewrite imports (keep `@/pages/planner` import for `PlannerExportImportSection`); edit `router.tsx`; remove emptied `components/settings/`.
- Tests: move + fix 4 `components/settings/__tests__/*`.
- Depends on: none.
- Verify: `tsc -b`, `lint`, `vitest run` for settings tests green.

### Phase 5: moderator slice
- Files: `git mv routes/ModeratorPage.tsx → pages/moderator/ModeratorPage.tsx`; `git mv components/moderation/BanDialog.tsx → pages/moderator/components/`; `git mv hooks/useModeratorData.ts, hooks/useModeratorMutations.ts → pages/moderator/hooks/`; `git mv schemas/ModeratorSchemas.ts → pages/moderator/schemas/`; `git mv types/ModeratorTypes.ts → pages/moderator/types/`; rewrite imports; edit `router.tsx`.
- DO NOT TOUCH: `components/moderation/BanStatusBanner.tsx` (+ its test), `hooks/useModeratorCommentDelete.ts`.
- Tests: none move (BanStatusBanner test stays); confirm it still passes.
- Depends on: none.
- Verify: `tsc -b`, `lint`, `vitest run` (BanStatusBanner test green); `components/moderation/` still contains only `BanStatusBanner.tsx` + `__tests__/`.

### Phase 6: keyword slice
- Files: `git mv routes/KeywordPage.tsx, routes/KeywordDetailPage.tsx → pages/keyword/`; `git mv components/keyword/* → pages/keyword/components/*`; rewrite imports (keyword data hooks / formatter infra / types / schemas referenced via shared `@/hooks`, `@/components/common`, `@/types`, `@/schemas` — NOT moved); edit `router.tsx` (2 paths; keyword detail loader stays inline).
- DO NOT TOUCH: `hooks/useKeyword{List,Detail}Data.ts`, `useKeywordFormatter.ts`, `useBattleKeywords.ts`, `types/KeywordTypes.ts`, `schemas/KeywordSchemas.ts`, `schemas/BattleKeywordsSchemas.ts`, `lib/keywordFormatter.ts`, `components/common/Formatted{Description,Keyword}.tsx`, `components/filter/BattleKeywordDropdown.tsx`.
- Tests: none move (keyword/hook tests stay shared); confirm green.
- Depends on: none.
- Verify: `tsc -b`, `lint`, `vitest run`; keyword filter dropdown + detail backlinks still work.

### Phase 7: full-suite verification
- Run `tsc -b`, `lint`, `vitest run` (full). Confirm `routes/` holds only `api/` + `auth/`; no moved slice has `index.ts`; all 8 route paths resolve.
- Depends on: Phases 1–6.

## Phase Dependencies
Sequential only (shared `router.tsx`): 1 → 2 → 3 → 4 → 5 → 6 → 7.

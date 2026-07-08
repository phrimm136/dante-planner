# Execution Plan (REPLANNED — page-slice model)

## Architecture pivot (decided 2026-06-24, supersedes the entity/feature/shared tier model)

The app is small and densely cross-referenced (game-data nouns reference each other by ID). The 3-tier FSD-lite model (entities/features/shared + sibling-entity ban) was over-fit and produced friction (dual-use classification, sibling-ban misfires on a legitimate DAG). **Replaced with a flat page-slice model:**

- **5 page slices**: `pages/{identity, ego, egoGift, themePack, abEvent}/` — each is a game-data noun that has its own **list route** (`/ego`) and **detail route** (`/ego/$id`).
- Each slice is **self-contained = the page**: it owns its list + detail route components, display components, data hooks, noun-owned lib, types, schemas, and a public API (`index.ts`).
- **Cross-page reuse is allowed and expected**: a component owned by one page (e.g. `EGOGiftCard` in `pages/egoGift`) is imported by another page (themePack/abEvent/keyword detail, planner) via that page's public API (`@/pages/<name>`). The slices form a DAG by discipline, not by lint.
- **One structural lint rule**: reach a slice only via its public API (`@/pages/<name>`), no deep internal imports. No directional/sibling bans. The router (composition root) is exempt (lazy code-split imports of page modules).
- **Out of scope (stay in `components/`/`features/` for now)**: the planner, deckBuilder, floorTheme, startGift/startBuff, and the egoGift planner-editing panes (store-writers: `ComprehensiveGiftSelectorPane`, `ComprehensiveGiftSummary`, `EGOGiftObservationEditPane`, `EGOGiftObservationSummary`) — these belong to a future `pages/planner` / `pages/deckBuilder` slice. Shared primitives (`components/ui`, `components/common`, `lib/`, `stores/`, cross-cutting `hooks/`) stay shared.
- SSOT conversion (`type = z.infer<typeof Schema>`) remains in scope per slice; complete schemas additively; never add production `.strict()` (save-path safety); strictness only in tests.

## Done so far (Phase A — architecture pivot)
- `src/entities/` → `src/pages/` (git mv); 82 files repointed `@/entities/` → `@/pages/`.
- eslint collapsed to the single page-public-API rule; sibling/feature/entity bans removed.
- identity, ego, egoGift, abEvent slices already hold display+hooks+lib+types+schemas (from the prior entity migration). Findings #1/#2 dissolved (no sibling ban; page→shared-lib is legal).
- Clean `tsc -b` green.

## Remaining Phases

### Phase B: Complete the `pages/themePack` slice
- Move into `pages/themePack/`: `components/themePack/*` (ThemePackList, ThemePackCardLink, …), `hooks/{useThemePackListData,useThemePackDetailData}`, `lib/themePackFilter`, `types/ThemePackTypes`, `schemas/ThemePackSchemas` (+ co-located tests). `ThemePackCard` already there.
- SSOT-convert `ThemePackTypes`; add `schemas/__tests__/ThemePackSchemas.test.ts` (strict only in test).
- Remove the themePack block from `schemas/index.ts`; repoint barrel consumers to `@/pages/themePack`.
- Repoint all consumers to `@/pages/themePack`: `floorTheme/{FloorThemeGiftSection,ThemePackSelectorPane,ThemePackViewer}`, `plannerViewer/{FloorGalleryTracker,ThemePackTrackerCard}`, `hooks/usePlannerSave`, `lib/{plannerRules,plannerValidation}`, `stores/usePlannerEditorStore`, routes.
- Verify: clean `tsc -b` + `vitest run` green; no old `@/components/themePack`, `@/hooks/useThemePack*`, `@/lib/themePackFilter`, `@/types/ThemePackTypes`, `@/schemas/ThemePackSchemas` outside the slice.

### Phase C: Move list + detail route components into their slices
- For each noun: `git mv routes/<Noun>Page.tsx → pages/<noun>/<Noun>Page.tsx` and `routes/<Noun>DetailPage.tsx → pages/<noun>/<Noun>DetailPage.tsx`.
  - Nouns/files: Identity(IdentityPage, IdentityDetailPage), EGO(EGOPage, EGODetailPage), EGOGift(EGOGiftPage, EGOGiftDetailPage), ThemePack(ThemePackPage, ThemePackDetailPage), AbEvent(AbEventPage, AbEventDetailPage).
- Inside each moved page component: convert imports of its OWN slice (`@/pages/<noun>` index) to RELATIVE (`./components/…`, `./hooks/…`) to avoid an index↔page circular import. Imports of OTHER slices stay `@/pages/<other>` (cross-page, legal). Shared deps stay `@/`.
- Re-export each page component from its slice `index.ts` (for the router).
- Rewire `lib/router.tsx`: `lazyRouteComponent(() => import('@/pages/<noun>/<Noun>Page'))` (deep path, code-split). Add an eslint override exempting `src/lib/router.tsx` from the page deep-import rule (composition root).
- Cross-noun detail pages keep their cross-page imports: EGODetailPage→`@/pages/identity`; ThemePackDetailPage→`@/pages/egoGift`,`@/pages/abEvent`; AbEventDetailPage→`@/pages/egoGift`; etc.
- Verify: clean `tsc -b` + `vitest run` green; routing unchanged (router resolves the new paths); no route component left in `routes/` for the 5 nouns.

### Phase D: Verify + boundary probe
- Clean `tsc -b`, full `vitest run` green.
- Boundary probe (page-model): deep import `@/pages/egoGift/components/EGOGiftCard` → lint error; public import `@/pages/egoGift` → passes; router deep-import allowed (exempt). Update `boundary-probe.config.mjs` to the page rule.
- Spec grep: no `@/entities/`, no stale `@/components/<noun>`/`@/types/<Noun>Types`/`@/schemas/<Noun>Schemas` for the 5 nouns outside their slices.
- Bundle sanity: no aggregate `src/pages/index.ts`; Tiptap stays in the planner chunk.

### Phase E: Docs + commit
- Update `requirements.md` decisions note, `frontend/CLAUDE.md`, `.claude/rules/frontend/data/schemas.md` to the page-slice model.
- Update memory (entities-migration decision → page-slice pivot).
- Single bulk commit (after review) — per user's deferred-commit instruction.

## Dependencies
A (done) → B → C → D → E. Strictly sequential. Verify green after B and after C.

# Task: Migrate the planner vertical into a `pages/planner/` page slice

Move all planner code — editor, viewer, list, deck builder, sub-panes, store, hooks,
libs, schemas, types — out of the scattered top-level roots (`src/routes/`,
`src/components/*`, `src/stores/`, `src/hooks/`, `src/lib/`, `src/schemas/`,
`src/types/`) into a single self-contained `src/pages/planner/` slice, mirroring the
six existing game-data slices. One atomic, behavior-preserving commit. SSOT
conversion (types → `z.infer`) applies where a schema already validates runtime data.

This is the planner decomposition that `docs/tasks/026-fe-entities-migration/` explicitly
**deferred** ("it does NOT decompose the planner (deferred)"). It completes the
page-slice architecture decided + landed in `19fdfb8`.

## Decisions

- **Planner is the write/composition layer, not a 7th reference browser** — the six
  existing slices are read-only game-data browsers; planner is user-authored documents
  built *on top of* them. Its state belongs *inside* the slice, not in a generic
  `src/stores/`. (evidence: brainstorm consensus; `usePlannerEditorStore.HotState`
  models a planner document — deck/equipment/notes/floorThemes/gifts.)
- **The store moves into the slice** at `pages/planner/stores/usePlannerEditorStore.tsx`
  — `DEFAULT` new `stores/` segment (existing slices have no store; Zustand state is
  distinct from data-fetching hooks). (evidence: no slice precedent; rationale = clarity.)
- **The 4 egoGift planner panes move into the slice** (`ComprehensiveGiftSelectorPane`,
  `ComprehensiveGiftSummary`, `EGOGiftObservationEditPane`, `EGOGiftObservationSummary`)
  → `pages/planner/components/egoGift/`. This breaks the only would-be cycle
  **structurally**: they are the sole importers of the store from outside planner, and
  they live in `components/egoGift/` (NOT the `pages/egoGift/` slice), so relocating
  them leaves a clean one-way `planner → egoGift` data edge. (evidence: verified all 4
  imported only by planner-side code; `pages/egoGift/index.ts` re-exports none of them;
  reverse-edge grep of `src/pages` for the store/planner modules returned empty.)
- **All sub-pane dirs move into the slice** — `deckBuilder/`, `startBuff/`, `startGift/`,
  `skillReplacement/`, `floorTheme/`, `plannerList/`, `plannerViewer/`, `planner/` →
  `pages/planner/components/<dir>/`. They are cohesive planner UI, not cross-domain
  utilities. (evidence: every dir is planner-only except the single `parseColorTags`
  edge resolved below.)
- **`parseColorTags`: repoint, do NOT extract.** Brainstorm planned to extract
  `formatBuffDescription` to `@/lib`. Superseded: `parseColorTags` is *defined* in the
  already-shared `@/components/common/ColoredText.tsx` and only **re-exported** through
  `startBuff/formatBuffDescription.tsx`. Fix = repoint `pages/themePack/ThemePackCard`
  (and its test) to import `parseColorTags` from `@/components/common/ColoredText`, then
  drop the now-dead re-export. `formatBuffDescription` then rides into planner with zero
  cross-slice edge. (evidence: `formatBuffDescription.tsx:5` `import { parseColorTags }
  from '@/components/common/ColoredText'`; `ColoredText.tsx:28` is the definition; this is
  the *only* `pages/* → moving-dir` import in the codebase.)
- **`SyncChoiceDialog` stays shared** in `components/dialogs/` — it is app-wide, not
  planner-only (post-login sync prompt). (evidence: imported by `GlobalLayout`, `Header`,
  `routes/auth/callback/google`, `stores/useFirstLoginStore`.)
- **`BatchConflictDialog`, `PlannerSection`, `PlannerNotFound`, `PlannerExportImportSection`
  move into the slice** — all planner-only. `PlannerExportImportSection` is rendered by
  `routes/SettingsPage` (a route, not a slice), so `SettingsPage` repoints to the
  `@/pages/planner` public API; routes-importing-a-slice is legal, no cycle. (evidence:
  importer greps.)
- **Tiptap / note infrastructure stays shared** — `components/noteEditor/`, `tiptap-*`
  dirs, `schemas/NoteEditorSchemas.ts`, `types/NoteEditorTypes.ts`, `lib/tiptap-utils.ts`,
  `lib/noteUtils.ts`, `hooks/use-tiptap-editor.ts`. They are used by comments and other
  features, not just planner. (evidence: brainstorm inventory; `noteEditor` consumed by
  `CommentEditor`/`SectionNoteDialog`.)
- **UNIFYING RULE — a module consumed by BOTH planner and a non-planner domain STAYS
  SHARED, never moves into the slice.** Moving it would forge a `slice→planner` (or
  shared-eager→planner-barrel) edge. The dual-consumer modules discovered by the symbol
  sweep, with their non-planner consumer:
  - **`lib/egoGiftEncoding.ts` STAYS in `@/lib`** — `pages/egoGift/EGOGiftSelectionList`
    imports `buildSelectionLookup` from it (and it imports egoGift data); it is egoGift/
    shared serialization, not planner-private. Moving it = `egoGift→planner` cycle.
    (evidence: `pages/egoGift/components/EGOGiftSelectionList.tsx:8`.)
  - **`types/StartBuffTypes.ts` STAYS in `@/types`** — it is dual-domain: keyword hooks
    (`useBattleKeywords`, `useKeywordListData`, `useKeywordDetailData`) and
    `schemas/BattleKeywordsSchemas` consume its `BattleKeywords` / `BattleKeywordI18nEntry`.
    (evidence: those imports of `@/types/StartBuffTypes`.)
  - **`hooks/useSseConnection.ts` STAYS in `@/hooks`** — it is the single app-wide SSE
    connection (mounted once in `GlobalLayout`), handling BOTH planner events
    (`PLANNER_UPDATE`/`SYNC_PLANNER`) and notification events; it imports from both the
    notification and planner domains. After the move it reaches the planner symbols it
    needs (`PlannerSseEventSchema`, `usePlannerSaveAdapter`, `plannerQueryKeys`,
    `userPlannersQueryKeys`) via the `@/pages/planner` public API — shared→slice is legal
    and the underlying module graph (hence bundle shape) is unchanged. (evidence:
    consumers `GlobalLayout`, `useUnreadCountQuery`; handles `NOTIFY_*` + `PLANNER_*`.)
- **`PlannerSchemas` + `PlannerListSchemas` move into `pages/planner/schemas/`** — this
  makes the "Tiptap out of the main chunk" rule *structural* (the schemas now live only
  inside the lazy-loaded slice). The `schemas/index.ts` exclusion **comment** for Planner
  is updated (the file is gone); the `NoteEditorSchemas` exclusion **stays** (it remains
  shared). (evidence: `schemas/index.ts:40-44`; lazy route via `lazyRouteComponent`.)
- **SSOT applies only where a schema validates runtime data** — `PlannerSchemas`,
  `PlannerListSchemas`, `StartGiftSchemas` exist → their types become `z.infer`, schemas
  completed structurally to match the runtime producer, drift fixed by *adding fields*,
  never `as any`. `DeckTypes` has **no** schema (transient in-memory editor state, never
  parsed from untrusted input) → it stays hand-written TS inside the slice; authoring Zod
  for it is out of scope. (`StartBuffTypes` also has no schema, but it stays SHARED per the
  unifying rule — not moved at all.) (evidence: `ls schemas` shows only Planner/PlannerList/
  StartGift; mirrors `26` which SSOT'd validated game-data, not transient UI state.)
- **SSOT must preserve Zod strip behavior on the save/load round-trip** — adding a field to a
  persisted-planner schema makes Zod now *retain* a key it previously stripped. The
  saved-planner fixture test asserts **round-trip identity** (load→re-serialize is
  byte-unchanged), not merely "does not reject". (evidence: adjacent to the `.refine`
  landmine; memory `note_byte_parity` — note byte counts are contract-sensitive.)
- **SSOT is structural-only, never adds a rejecting `.refine`** — completing a load-path
  schema must not introduce validation that can fail, or it triggers the blast-radius bug.
  (evidence: memory `saveable_planner_schema_blast_radius` — a strict `.refine` anywhere in
  the `SaveablePlannerSchema` tree discards the *entire* planner on load.)
- **Internal imports become relative, external consumers use the public API** — within the
  slice use `./` / `../`; shared deps stay `@/…`; non-planner code imports `@/pages/planner`
  (never a deep path). (evidence: migration playbook memory; `eslint.config.js` page-slice
  rule.)
- **Route components live at the slice root, router deep-imports them** — e.g.
  `pages/planner/PlannerMDPage.tsx`, lazy-imported by `router.tsx` (lint-exempt). Route
  components are NOT in the public API (matches `pages/ego`: `EGOPage` is root-level and not
  in `index.ts`). (evidence: `pages/ego/EGOPage.tsx` + `router.tsx:273`; `pages/ego/index.ts`
  omits the page components.)
- **Public API (`pages/planner/index.ts`) exports exactly the symbols external code needs**
  — verified by the symbol sweep:
  - `PublishedPlannerCard` (for `home/CommunityPlansSection`)
  - `PlannerExportImportSection` (for `routes/SettingsPage`)
  - `fetchPublishedPlanner`, `publishedPlannerQueryKeys` (for `router.tsx` loaders)
  - `PlannerSseEventSchema`, `usePlannerSaveAdapter`, `plannerQueryKeys`,
    `userPlannersQueryKeys` (for shared `hooks/useSseConnection`)

  Re-exports must be `export { x } from './x'` form (tree-shakeable) so the eager
  `useSseConnection` import of 4 symbols does not drag planner components into the main
  chunk. No aggregate `src/pages/index.ts` barrel. (evidence: external-consumer symbol
  sweep; `26` "no aggregate barrel" rule.)
- **One atomic commit**, gated green before commit (user decision, overriding `26`'s
  3-commit split — the keystone cycle-break is already read-only verified, so the residual
  risk is mechanical import-rewrite only).

## Resolved Ambiguities
> Proof of closure. Every row resolved; nothing deferred to plan.

| Question | Resolution | Source |
|----------|------------|--------|
| What internal layout do existing slices use? | Route components at slice ROOT; `components/ hooks/ lib/ schemas/ types/` subdirs; public-API `index.ts`. Flat sub-feature folders preserved under `components/`. | Codebase `src/pages/ego`, `src/pages/extraction` |
| Where does the Zustand store live in a slice? | New `pages/planner/stores/` segment. | `DEFAULT` (no precedent; rationale recorded) |
| Are the 4 egoGift panes rendered by the egoGift slice? | No — imported only by planner editor/viewer; `pages/egoGift/index.ts` re-exports none. Safe to move. | Codebase (importer grep; `pages/egoGift/index.ts`) |
| Is the `formatBuffDescription` cross-edge a real extraction? | No. `parseColorTags` is defined in shared `common/ColoredText`; only re-exported via startBuff. Repoint themePack to ColoredText + drop dead re-export. | Codebase `formatBuffDescription.tsx:5`, `ColoredText.tsx:28` |
| Is `SyncChoiceDialog` planner-only? | No — app-wide (GlobalLayout/Header/auth-callback/useFirstLoginStore). Stays shared. | Codebase (importer grep) |
| Does any `pages/*` slice import planner (reverse edge / cycle risk)? | None. Grep of `src/pages` for store/planner modules empty. | Codebase (reverse-edge grep) |
| Which external (non-planner, non-router) code consumes moved symbols? | `home/CommunityPlansSection` (`PublishedPlannerCard`), `routes/SettingsPage` (`PlannerExportImportSection`), `pages/themePack/ThemePackCard` (`parseColorTags`), `hooks/useSseConnection` (4 SSE/save symbols). Repoint to public API / ColoredText. | Codebase (full symbol sweep) |
| Does any `pages/*` SLICE file import a moving symbol (cycle)? | One: `pages/egoGift/EGOGiftSelectionList` imports `buildSelectionLookup` from `lib/egoGiftEncoding`. Resolved by keeping `egoGiftEncoding` SHARED in `@/lib` (not moving it). No other slice inbound edge exists. | Codebase (full symbol sweep) |
| Is `lib/egoGiftEncoding` planner-private? | No — dual consumer (egoGift slice + planner). Stays `@/lib`. | Codebase (`EGOGiftSelectionList.tsx:8`) |
| Is `types/StartBuffTypes` planner-private? | No — keyword hooks + `BattleKeywordsSchemas` consume its keyword types. Stays `@/types`. | Codebase (keyword-hook imports) |
| Is `hooks/useSseConnection` planner-private? | No — app-wide unified SSE (planner + notification events), mounted in `GlobalLayout`. Stays `@/hooks`; reaches planner symbols via public API. | Codebase (consumers + event types) |
| Was the initial move-set complete? | No — sweep added planner hooks (`useMDGesellschaftData`, `useMDGesellschaftFilters`, `useMDUserFilters`, `useTrackerState`, `useStartBuffData`, `useStartBuffSelection`, `useStartGiftPools`) and libs (`deckFilter`, `startGiftCalculator`). Now incorporated. | Codebase (full symbol sweep) |
| Do Header/HeaderNav/Footer import planner modules? | No — route-path `Link`s only; no change. | Codebase (grep returned only CommunityPlansSection) |
| How far does SSOT reach? | Only schema-backed runtime types: `Planner`, `PlannerList`, `StartGift` → `z.infer`. `Deck`/`StartBuff` types have no schema → stay TS. | Codebase `ls schemas`; Convention (`26` scope) |
| Do test files move? | Yes — `__tests__/` dirs travel with their subjects (store, deckBuilder, startBuff, startGift, planner, plannerList, plannerViewer, egoGift panes, planner hooks). | Codebase (find `__tests__`); Convention (test-colocation memory) |
| Does the project data-driven spec addendum apply? | No — pure structural refactor, zero data-file/pipeline changes. Marked N/A. | `docs/spec.md` scope ("Features that consume raw game data") |
| Test runner / gates? | `vitest` + `tsc -b` (FE lint is broken repo-wide → not a gate; prove boundary with a throwaway `no-restricted-imports`-only flat config). | Codebase `package.json`; memory `arch-fe-yarn-lint-is-broken-repo` |

## Description

Relocate the entire planner vertical into `src/pages/planner/` as one self-contained
page slice, behavior byte-identical. Concretely:

1. **Create the slice skeleton** `src/pages/planner/{components,hooks,lib,schemas,types,stores}/`
   and a public-API `index.ts`.
2. **Move route components** (8) to the slice root and repoint `router.tsx` lazy-imports.
3. **Move the store + the 4 egoGift panes together** (the cycle-breaking keystone).
4. **Move all sub-pane component dirs, planner components, dialogs, common + settings
   planner components.**
5. **Move planner hooks, libs, schemas, types.** Convert internal absolute imports to
   relative; apply SSOT to schema-backed types.
6. **Repoint the 3 external consumers** to the public API, and repoint
   `themePack/ThemePackCard` `parseColorTags` to `@/components/common/ColoredText`;
   delete the dead re-export in `formatBuffDescription`.
7. **Update `schemas/index.ts`** (drop the moved Planner schema exclusion comment; keep
   NoteEditor).
8. **Author `pages/planner/index.ts`** exporting exactly the externally-consumed symbols.
9. **Verify green** (`tsc -b`, `vitest`, boundary probe), then one commit.

## Scope (READ for context)

- `src/pages/ego/`, `src/pages/extraction/` — slice convention to mirror (root route
  components + `index.ts` public-API shape).
- `src/lib/router.tsx` — 8 planner route registrations + the `usePublishedPlannerQuery`
  loader import.
- `eslint.config.js` (page-slice rule + router exemption), `src/schemas/index.ts`
  (exclusion comments).
- `src/components/startBuff/formatBuffDescription.tsx`, `src/components/common/ColoredText.tsx`.
- `docs/tasks/026-fe-entities-migration/{instructions,plan}.md` — SSOT + commit-discipline precedent.

## Target (CREATE / MODIFY / MOVE)

**Create:** `src/pages/planner/index.ts` + the slice subdirs.

**Move into `src/pages/planner/`:**
- Route components (root): `PlannerPage`, `PlannerMDPage`, `PlannerMDNewPage`,
  `PlannerMDEditPage`, `PlannerMDDetailPage`, `PlannerMDGesellschaftPage`,
  `PlannerMDGesellschaftDetailPage`, `DeckBuilderPage` (from `src/routes/`).
- `stores/usePlannerEditorStore.tsx` (+ `__tests__`).
- `components/`: `planner/`, `plannerList/`, `plannerViewer/`, `deckBuilder/`,
  `startBuff/`, `startGift/`, `skillReplacement/`, `floorTheme/` (+ their `__tests__`);
  the 4 egoGift panes (+ their 2 `__tests__`) → `components/egoGift/`; `BatchConflictDialog`,
  `PlannerSection`, `PlannerNotFound`, `PlannerExportImportSection`.
- `hooks/`: the planner hooks (`usePlanner*`, `useMDUserPlannersData`, `useMDUserFilters`,
  `useMDGesellschaftData`, `useMDGesellschaftFilters`, `useSavedPlannerQuery`,
  `usePublishedPlannerQuery`, `useHiddenPlannersQuery`, `useModeratorPlannerDelete`,
  `useTrackerState`, `useStartBuffData`, `useStartBuffSelection`, `useStartGiftPools`) +
  `__tests__`. (NOT `useSseConnection`, `useBattleKeywords`, `useKeyword*Data` — shared.)
- `lib/`: `plannerApi`, `plannerValidation`, `plannerValidationErrors`, `plannerRules`,
  `plannerContentExtractors`, `deckCode`, `deckFilter`, `startGiftCalculator` + `__tests__`.
  (NOT `egoGiftEncoding` — shared.)
- `schemas/`: `PlannerSchemas`, `PlannerListSchemas`, `StartGiftSchemas` + `__tests__`.
- `types/`: `PlannerTypes`, `PlannerListTypes`, `MDPlannerListTypes`, `PlannerSearchTypes`,
  `DeckTypes`, `StartGiftTypes`. (NOT `StartBuffTypes` — shared.)

**Modify (stay in place, repoint imports only):**
- `src/lib/router.tsx` — route deep-imports → `@/pages/planner/<Page>`; loader helper →
  `@/pages/planner`.
- `src/routes/SettingsPage.tsx` → `PlannerExportImportSection` from `@/pages/planner`.
- `src/components/home/CommunityPlansSection.tsx` → `PublishedPlannerCard` from `@/pages/planner`.
- `src/hooks/useSseConnection.ts` → its 4 planner symbols from `@/pages/planner`.
- `src/pages/themePack/components/ThemePackCard.tsx` (+ test) → `parseColorTags` from
  `@/components/common/ColoredText`.
- `src/schemas/index.ts` — update exclusion comments; remove `StartGiftSchemas` export if
  it has no non-planner consumer (grep `StartEgoGiftPoolsSchema`; if planner-only, de-barrel).
- `src/components/startBuff/formatBuffDescription.tsx` (now in planner) — delete dead
  `parseColorTags` re-export.

**Do NOT move (shared — dual consumer or generic infra):** `components/noteEditor/`,
`tiptap-*`, `dialogs/SyncChoiceDialog`, `common/ColoredText`, `schemas/NoteEditorSchemas`,
`types/NoteEditorTypes`, `lib/{tiptap-utils,noteUtils}`, `hooks/use-tiptap-editor`,
**`lib/egoGiftEncoding`** (egoGift slice consumer), **`types/StartBuffTypes`** (keyword
consumers), **`hooks/useSseConnection`** (app-wide SSE), `hooks/{useBattleKeywords,
useKeywordListData,useKeywordDetailData}` (keyword domain), `schemas/BattleKeywordsSchemas`.

## Impact Analysis

- **Files moved:** ~100 (components, hooks, libs, schemas, types, routes, tests).
- **External consumers repointed:** `router.tsx`, `SettingsPage`, `CommunityPlansSection`,
  `themePack/ThemePackCard` (+ test), `useSseConnection` — 5 sites, all verified by the
  symbol sweep.
- **Cycle guard:** the sweep found exactly one `pages/*`-slice inbound edge
  (`egoGift/EGOGiftSelectionList → egoGiftEncoding`), neutralized by keeping
  `egoGiftEncoding` shared. Re-run the sweep after the move; it MUST report zero
  `pages/*`→`@/pages/planner` import edges except via egoGift's own data (one-way).
- **Internal imports rewritten:** every planner-internal `@/…` reference to a co-moved
  module becomes relative.
- **Dependencies:** planner depends downhill on `@/pages/{identity,ego,egoGift,themePack}`
  (public API), `@/components/common`, `@/components/noteEditor`, `@/lib/*` shared utils,
  `@/schemas/NoteEditorSchemas`, `@static/*`. All one-way.
- **Ripple:** mis-rewritten import → compile break (caught by `tsc -b`); a missed external
  consumer → deep-import lint violation (caught by boundary probe); a stray eager import of
  `@/pages/planner` → Tiptap leaks into main chunk (watch chunk output).

## Risk Assessment

- **Edge cases:** re-export barrels masking true owners (the `parseColorTags` case — sweep
  for others before assuming a dir is a leaf); test files left behind their subjects;
  `StartGiftSchemas` barrel membership.
- **Performance/bundle:** `PlannerSchemas`/`NoteEditorSchemas` must not re-enter the main
  chunk; no aggregate `pages/index.ts` barrel.
- **Security:** none (no auth/trust-boundary change; pure relocation).

## Boundaries & Invariants

- **Trust/ownership boundary:** unchanged. The slice boundary is `pages/planner/index.ts`;
  the data trust boundary (Zod parse of persisted/published planners) stays exactly where
  it is.
- **Invariant 1 — behavior is byte-identical.** No runtime logic changes; only file
  locations and import specifiers (and SSOT type derivations that don't alter runtime).
- **Invariant 2 — the import graph stays a DAG; `egoGift` imports nothing from `planner`.**
- **Invariant 3 — no eager (non-lazy) module imports `@/pages/planner`;** Tiptap and the
  planner/note schemas stay out of the main chunk.
- **Invariant 4 — no rejecting `.refine` is added to any schema in the
  `SaveablePlannerSchema` composition tree.**
- **Invariant 5 — a slice is reached only via its public API;** internal deep paths are
  never imported from outside (router exempt).

## Failure Modes
> This is a compile-/bundle-/graph-integrity refactor, not a concurrent runtime feature;
> "triggers" are mechanical-move hazards.

| Invariant | Trigger (how it breaks) | Response | Test |
|-----------|-------------------------|----------|------|
| Inv 1 byte-identical | A move "tidies" logic or SSOT adds a constraint | Pure relocation rule; SSOT structural-only | `vitest` full suite green pre/post |
| Inv 1 byte-identical | Re-run of the move on a half-migrated tree (idempotency) | Move is git-tracked; partial state = non-compiling tree, not corrupt data | `tsc -b` gates the single commit |
| Inv 2 DAG / no cycle | A `pages/*` slice imports a MOVED symbol → `slice→planner` cycle, **invisible to lint** (a `@/pages/planner` public-API import is legal; "DAG by discipline, not by lint") | Don't move dual-consumer modules (`egoGiftEncoding`, `StartBuffTypes`, `useSseConnection` stay shared); panes move INTO planner | **Pre-move symbol sweep** (grep every moving lib/schema/type/hook by symbol, partition `pages/*` importers) — NOT the boundary probe, which cannot see public-API cycles |
| Inv 3 bundle | `startBuff` rides into planner but `themePack` still deep-imports `parseColorTags` → `themePack→planner` | Repoint to `ColoredText` FIRST; drop dead re-export | `vite build` chunk inspection; boundary probe |
| Inv 4 blast-radius | SSOT "completion" adds `.refine` (rejects) OR a new field changes Zod strip (round-trip drift) on the load-path schema | Add fields only, no rejecting refine; enforce validity at editor/save-guard layer | Saved-planner fixture: load→re-serialize is **byte-identical** (`vitest`) |
| Inv 5 public API | An external consumer keeps a deep `@/pages/planner/...` path | Repoint to `@/pages/planner`; add symbol to `index.ts` | Boundary probe |

### Visualized Failure
> Worst row: Inv 3 (silent bundle regression).

1. Implementer moves `startBuff/` into `pages/planner/components/startBuff/` and runs the
   global import rewrite.
2. The rewrite repoints `themePack/ThemePackCard`'s `parseColorTags` import from
   `@/components/startBuff/formatBuffDescription` to `@/pages/planner` (because the file
   moved), forging `themePack → planner`.
3. `tsc -b` still passes (TypeScript tolerates import cycles). Vitest passes. **Nothing is
   red.** But Vite's lazy-chunk graph now entangles the lightweight `themePack` page with
   the heavy planner chunk → opening `/theme-pack` silently pulls Tiptap + the whole
   planner bundle.
4. → **Response intervenes at step 2:** because the spec mandates repointing
   `parseColorTags` to its true shared home (`@/components/common/ColoredText`) *before* the
   move and deleting the re-export, the rewrite has no startBuff path to follow — the edge
   is never created. The boundary probe + `vite build` chunk diff are the backstops.

## Done When

- [ ] `src/pages/planner/` contains the full vertical (routes, store, components, hooks,
      libs, schemas, types) with a public-API `index.ts`.
- [ ] No planner code remains under `src/routes/Planner*`, `src/routes/DeckBuilderPage`,
      `src/stores/usePlannerEditorStore*`, `src/components/{planner,plannerList,plannerViewer,
      deckBuilder,startBuff,startGift,skillReplacement,floorTheme}`, the 4 egoGift panes,
      `src/hooks/usePlanner*` (and the moved siblings), `src/lib/planner*`+`deckCode`+
      `deckFilter`+`startGiftCalculator`, `src/schemas/Planner*`+`StartGiftSchemas`,
      `src/types/{Planner*,MDPlannerList,Deck,StartGift}Types`.
- [ ] Shared modules untouched in place: note/Tiptap infra, `SyncChoiceDialog`,
      `lib/egoGiftEncoding`, `types/StartBuffTypes`, `hooks/useSseConnection`, keyword hooks.
- [ ] Pre/post symbol sweep: zero `pages/*`-slice imports of `@/pages/planner` except
      planner's own one-way data use of egoGift/themePack/etc.
- [ ] `themePack/ThemePackCard` imports `parseColorTags` from `@/components/common/ColoredText`;
      the `formatBuffDescription` re-export is gone.
- [ ] The 3 external consumers (`router`, `SettingsPage`, `CommunityPlansSection`) import via
      `@/pages/planner`.
- [ ] Schema-backed planner types are `z.infer`; no `as any`; no new rejecting `.refine`.
- [ ] `yarn --cwd frontend tsc -b` exits 0.
- [ ] `yarn --cwd frontend vitest run` — all existing tests pass in their new locations.
- [ ] Boundary probe (throwaway `no-restricted-imports`-only flat config) reports zero
      deep-import violations and zero `egoGift→planner` edges.
- [ ] `yarn --cwd frontend vite build` chunk output shows no Tiptap/planner-schema code in
      the main/entry chunk.
- [ ] Landed as one commit.

## Test Plan

> Project data-driven spec addendum (`docs/spec.md`) is **N/A** — no game-data files or
> pipeline are touched; this is a structural code move.

### Test Runner
- Framework: **vitest** (frontend); **`tsc -b`** for type integrity.
- Run (redirect per project convention to `/tmp/`):
  - `yarn --cwd frontend tsc -b > /tmp/fe-typecheck-<session-id>-planner.log`
  - `yarn --cwd frontend vitest run > /tmp/fe-vitest-<session-id>-planner.log`

### Tests to Write / Carry
- [ ] No NEW behavior tests — refactor is behavior-preserving. The existing moved suites
      ARE the safety net.
- [ ] Every `__tests__/` dir moves with its subject and passes unchanged (store, deckBuilder,
      startBuff, startGift, planner, plannerList, plannerViewer, egoGift panes, planner hooks).
- [ ] Failure-Modes realization: the moved `usePlannerEditorStore` / `usePlannerSave` /
      `PlannerSchemas` suites cover Inv 1 & Inv 4; the **symbol sweep** covers Inv 2; the
      boundary probe + chunk-diff cover Inv 3/5.
- [ ] Inv 4 round-trip guard: extend/confirm a `PlannerSchemas` test that loads a saved
      planner fixture and asserts **load→re-serialize is byte-identical** (catches SSOT
      field-completion changing Zod strip behavior), not merely "does not throw".

## Verification

### Manual
1. Run `tsc -b` and `vitest run` — both green.
2. Run the boundary probe; confirm zero violations and no `egoGift→planner` import.
3. `vite build`; inspect chunk manifest — planner + Tiptap only in lazy planner chunks.
4. Smoke the app: `/planner`, `/planner/md`, `/planner/md/new`, `/planner/md/$id/edit`,
   `/planner/md/$id`, `/planner/md/gesellschaft`, `/planner/deck`, `/settings`
   (export/import), home community section, a theme-pack detail (color tags render).

### Edge Cases
- [ ] Re-export barrels: before treating any dir as a leaf, grep its exports' true origins
      (the `parseColorTags` lesson).
- [ ] `StartEgoGiftPoolsSchema` barrel membership: if no non-planner consumer, de-barrel; else
      keep shared / repoint to public API.
- [ ] Saved-planner load: an existing saved fixture still deserializes (Inv 4 guard).

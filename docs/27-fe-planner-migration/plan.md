# Execution Plan — Planner → `pages/planner/` slice

## Phase Summary

Bottom-up, green-at-every-phase, behavior-preserving relocation of the planner vertical
into `src/pages/planner/`, landing as one commit. The order front-loads the cycle-removal
(Phase 0 kills the only `pages/*`→moving-dir edge before any file moves), does the bulk
relocation + import rewrite as one atomic mechanical pass (Phase 1, because the planner
module graph is one tightly-coupled cluster — a finer split would double the import churn
and can't stay green), then layers SSOT (Phase 2) and the invariant gates (Phase 3).

Gates every phase: `yarn --cwd frontend tsc -b` + `yarn --cwd frontend vitest run` green
(lint is deferred repo-wide; boundary proven by a throwaway probe in Phase 3).

## Phases

### Phase 0: Prep — remove the cross-edge + scaffold
- Files:
  - `src/pages/themePack/components/ThemePackCard.tsx` (+ `__tests__/ThemePackCard.test.tsx`)
    — repoint `parseColorTags` to `@/components/common/ColoredText`.
  - `src/components/startBuff/formatBuffDescription.tsx` — delete the dead
    `import { parseColorTags } … ; export { parseColorTags }` re-export.
  - Create `src/pages/planner/{components,hooks,lib,schemas,types,stores}/` + stub `index.ts`.
- Tests: existing `ThemePackCard.test.tsx` passes with the new import.
- Depends on: none.
- Verify: `tsc -b` + `vitest run` green; grep shows zero `pages/*`→`@/components/startBuff`.

### Phase 1: Mass relocation + import rewrite (keystone)
- Files: all MOVE-set members from `instructions.md` § Target →
  `src/pages/planner/` (route comps at root; `components/<subdir>/`; `hooks/`; `lib/`;
  `schemas/`; `types/`; `stores/`), including every co-located `__tests__/`.
  - Keystone sub-step: store + the 4 egoGift panes move together.
- Import rewrite: co-moved refs → relative; shared refs (`@/lib/egoGiftEncoding`,
  `@/types/StartBuffTypes`, `@/components/common`, `@/components/noteEditor`,
  `@/schemas/NoteEditorSchemas`, `@/lib/*`, `@static/*`) stay `@/`.
- External consumers repointed: `src/lib/router.tsx` (8 route deep-imports + the published
  loader helper), `src/routes/SettingsPage.tsx`, `src/components/home/CommunityPlansSection.tsx`,
  `src/hooks/useSseConnection.ts`.
- Public API `src/pages/planner/index.ts`: `PublishedPlannerCard`,
  `PlannerExportImportSection`, `fetchPublishedPlanner`, `publishedPlannerQueryKeys`,
  `PlannerSseEventSchema`, `usePlannerSaveAdapter`, `plannerQueryKeys`,
  `userPlannersQueryKeys` — all `export { x } from './…'` (tree-shakeable).
- `src/schemas/index.ts`: drop the moved-Planner exclusion comment; remove
  `StartEgoGiftPoolsSchema` export if planner-only (grep first); keep `NoteEditorSchemas`.
- Tests: all moved suites run from their new locations, unchanged, passing.
- Depends on: Phase 0.
- Verify: `tsc -b` + `vitest run` green; no planner files remain under old roots (per
  `instructions.md` § Done When list).

### Phase 2: SSOT conversion
- Files: `pages/planner/types/{PlannerTypes,PlannerListTypes,StartGiftTypes}.ts` derive
  `z.infer` of `pages/planner/schemas/{PlannerSchemas,PlannerListSchemas,StartGiftSchemas}.ts`;
  complete schemas structurally to match the runtime producer (add fields, never `as any`,
  never a rejecting `.refine` on the load path). `DeckTypes` stays hand-written.
- Tests: extend/confirm a `PlannerSchemas` test asserting saved-planner load→re-serialize
  is **byte-identical** (round-trip), guarding Zod strip drift + the `.refine` landmine.
- Depends on: Phase 1.
- Verify: `tsc -b` + `vitest run` green; round-trip test passes.

### Phase 3: Invariant gates
- Symbol sweep: zero `pages/*`→`@/pages/planner` edges (except planner's own one-way data
  use). Boundary probe: throwaway `no-restricted-imports`-only flat eslint config → zero
  deep-import violations. `vite build` chunk-diff vs pre-migration baseline → no Tiptap /
  planner-schema in the entry chunk. Full `tsc -b` + `vitest run`.
- Depends on: Phase 2.
- Verify: all four checks pass. (Commit is user-initiated per `commit-process`.)

## Phase Dependencies
- Strictly sequential: Phase 0 → 1 → 2 → 3. No parallelism — Phase 1 is one atomic
  green-keeping unit; later phases build on its tree.

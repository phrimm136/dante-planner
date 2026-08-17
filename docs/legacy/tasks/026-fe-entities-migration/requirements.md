> **PIVOT (2026-06-24):** During execution this migration was redirected from the FSD-lite `entities/features/shared` tier model to a flatter **page-slice** model — `src/pages/<noun>/` for the 5 game-noun pages (identity, ego, egoGift, themePack, abEvent), each owning its list+detail route components + display + hooks + lib + types + schemas + public-API `index.ts`, with **free cross-page imports** via public API and a single lint rule (public-API-only; router exempt). The dense by-ID cross-references (egoGift↔themePack↔abEvent↔keyword) made the entity/feature tier + sibling-ban over-fit. The decisions below are retained for history; the executed shape and rationale are in `plan.md`.

# Task: Migrate five shared entities (`identity`, `ego`, `egoGift`, `themePack`, `abEvent`) into an `entities/` tier

This is phase 2 of the FE feature-first migration. The extraction pilot (`docs/tasks/023-fe-feature-first-migration/`, landed in `da7235b`) proved the slice mechanics on a true *leaf*. This phase introduces the **`entities/` tier** the pilot explicitly deferred ("identity/ego/egoGift are shared entities… need the `entities/` layer decision first"), and migrates the five game-noun entities into it.

The architecture is a 3-tier **FSD-lite** model: `routes/ (pages) → features/ → entities/ → shared/`, imports pointing down only. This phase creates `src/entities/` and the entity tier of lint enforcement; it does **not** decompose the planner (deferred) or physically relocate the shared tier (`lib/`, `components/common`, `components/ui` stay in place).

## Decisions

- **3-tier model (`shared → entities → features`) + `routes/` as page layer** — because two tiers cannot express the dual-use case: `EGOGiftCard` (pure display) is too domain-specific for `shared` and too reusable to belong to `features/planner`. A dedicated `entities/` tier lets `features/planner` import the card downhill while the entity never imports the planner. (evidence: dual-use confirmed — `EGOGiftCard` is props-only display; `ComprehensiveGiftSelectorPane` writes `usePlannerEditorStore`.)
- **Tier discriminator = domain → behavior → fan-in** — (1) no game domain → `shared`; (2) owns state/mutations/workflow → `features`; (3) else → `entities`. Fan-in confirms (an entity has multi-feature fan-in). (evidence: applied per file below.)
- **Five entities this batch: `identity`, `ego`, `egoGift`, `themePack`, `abEvent`** — `keyword` deliberately excluded by the user (revisit later; it interacts with `useColorCodes`/`useKeywordFormatter`).
- **Layered internal segments, matching the pilot (NOT flat):** `components/ hooks/ lib/ types/ schemas/ index.ts`. A slice creates only the segments it needs. (evidence: user chose to keep the pilot's layered subdirs; `features/extraction/` shape.)
- **SSOT conversion IS in scope (user decision):** every entity type becomes `z.infer<typeof Schema>`; schemas are completed to match what the runtime producer emits; surfaced drift is fixed by adding fields, never `as any`. Runtime behavior stays byte-identical; types/schemas may change to eliminate drift. (evidence: user answer "Move + SSOT convert".)
- **Shared tier stays physically in place** — `src/lib/`, `src/components/common`, `src/components/ui`, and cross-cutting `src/hooks/` are the shared tier *conceptually*; they are NOT moved into a `src/shared/` dir this phase. Splitting god files (`constants.ts` 189-file fan-in, `utils.ts`, `api.ts`) is out of scope — zero coupling payoff, unreviewable blast radius. (DEFAULT, rationale: consistent with deferring the planner and not touching god files.)
- **Three atomic, green commits, front-loaded risk:**
  - **Commit 1 — Prep:** relocate 3 misfiled components + add the `entities/` eslint zones. Mandatory first (the misfiles would make the new lint zones error on legal-intent code).
  - **Commit 2 — Leaf entities:** `identity`, `ego`, `abEvent` (pure codex, no planner consumers to repoint beyond routes/deckBuilder).
  - **Commit 3 — Dual-use entities:** `egoGift`, `themePack` + repoint planner viewer/editor. Isolated so a boundary failure unwinds to one commit.
  Sequencing = front-load: the pilot proved mechanics; the only unproven thing is the `entities → features` boundary, exercised only by `egoGift`/`themePack`, so validate it in commit 3 rather than after 5 boilerplate moves. (evidence: user confirmed "y" to the 3-commit split.)
- **Enforcement extends the committed built-in `no-restricted-imports` pattern (NO `import-x`)** — the landed `eslint.config.js` (lines 27–67) already uses generic `src/features/**` globs, not the `import-x/no-restricted-paths` the pilot *doc* proposed and not a hardcoded `extraction` path. Mirror that for `entities/`. (evidence: `eslint.config.js:27-67`.)
- **`shared ↛ entities` enforcement is DEFERRED to a later phase** — feature logic still physically lives in `lib/`/`hooks/` (`deckCode`, `plannerRules`, `usePlannerSave`, …) and legally imports entity types as un-migrated feature code. Enforcing shared↛entity now would force migrating the planner (deferred). This phase enforces only: public-API-only access to entities, and entity↛feature / entity↛sibling-entity. (evidence: "shared-tier violation check" grep lists 14 `lib/`+`hooks/` files importing moving types.)
- **No aggregate barrel** — never create `src/entities/index.ts` or `src/features/index.ts`. Each slice exposes only its own `index.ts`. (evidence: `schemas/index.ts:83-87` excludes `PlannerSchemas`/`NoteEditorSchemas` to keep Tiptap out of the main chunk; an aggregate barrel would re-pull Tiptap.)

## Resolved Ambiguities

| Question | Resolution | Source |
|----------|------------|--------|
| Did the extraction pilot land? Is `import-x` installed? | Landed in `da7235b`. `import-x` NOT installed; committed config uses built-in `no-restricted-imports` with generic `src/features/**` globs. | Codebase `eslint.config.js:27-67`, `package.json` |
| Test runner / commands? | Vitest (`yarn test` / `vitest run`), `tsc -b` (`yarn typecheck`), `eslint .` (`yarn lint`). Yarn. Redirect logs to `/tmp/<prefix>-<session>-<suffix>.log`. | Codebase `package.json` scripts |
| Does `src/entities` or `src/shared` exist? | Neither. Create `src/entities/`; shared stays in place. | Codebase (`ls`) |
| Is `ThemePackCard` planner-coupled (safe to move into `entities/themePack`)? | Safe — props-only (`ThemePackCardProps`), no `usePlannerEditorStore`. | Codebase `components/floorTheme/ThemePackCard.tsx:16,35` |
| Is `ego → identity` real coupling? | No — `ego` imports only `SkillDescription`+`CoinDisplay` (shared skill primitives misfiled in `identity/`). Relocating them to `components/common` dissolves the edge. | Codebase `components/ego/EGOSkillCard.tsx:5`, `EGOSkillInfoPanel.tsx:7` |
| Is `useSearchMappings` entity-owned? | No — consumed by `deckBuilder`, `ego`, `egoGift`, `identity`, `plannerViewer`, `lib/deckFilter`. Cross-cutting → stays shared (`hooks/`). | Codebase (consumer grep) |
| Is `useColorCodes` entity-owned? | No — consumed by `egoGift` AND `hooks/useKeywordFormatter` (a shared hook). Moving it into `egoGift` would make a shared hook import an entity. Stays shared (`hooks/`). | Codebase (consumer grep) |
| `useTraitsI18n` / `usePanicInfo` ownership? | Identity-only (`TraitsI18n.tsx`, `SanityI18n.tsx`). Move into `entities/identity/hooks`. | Codebase (consumer grep) |
| Do entity `__tests__` move with the slice? | Yes — co-located `__tests__/` move with their components/lib and repoint to feature-local paths. | Convention (pilot precedent) |
| Where do `SkillDescription`/`CoinDisplay` land? | `src/components/common/` (existing shared component dir). | Convention |
| Which `lib/` files are entity-owned vs feature/shared? | Entity: `egoGiftSort`, `egoGiftFilter`, `egoGiftUtils`, `EGOGiftObservationData`, `themePackFilter`, `abEventFilter`, `abEventTextResolver`, `formatSanityCondition`, `sanityConditionFormatter`. Stay shared/transitional-feature: `entitySort` (identity+ego), `deckCode`, `deckFilter`, `plannerRules`, `plannerValidation`, `egoGiftEncoding`, `egoPassiveSelection`, `floorGiftBucketing` (planner save/selection behavior). | Codebase (consumer grep) + behavior test |
| SSOT (`z.infer`) conversion in scope? | Yes — convert types + complete schemas per entity, fix drift inline. | User |
| Does moving entity schemas break `@/schemas` barrel consumers? | Remove the 5 entities' re-export blocks from `schemas/index.ts`; repoint any `@/schemas` barrel consumer of those symbols to `@/entities/<name>`. Consumer set defined by `grep -rl "from '@/schemas'"` cross-checked against the removed symbols. | Codebase `schemas/index.ts:15-21,23-29,33-39,49-51,75-81,109-114` |
| Does relocating route imports break TanStack Router? | No — routing is code-based (`router.tsx`, 30 `createRoute`); `routeTree.gen.ts` empty. Entities are not under `routes/`; only repoint import paths. `__root.tsx` stays. | Codebase `lib/router.tsx`, `routeTree.gen.ts` |

## Description

For each of the five entities, create `src/entities/<name>/` with `components/ hooks/ lib/ types/ schemas/ index.ts`, move that entity's vertical (display components, data/i18n hooks, entity-owned lib, types, schemas, co-located tests) into it, convert every type to `z.infer<typeof Schema>` (completing schemas to match runtime and fixing surfaced drift), expose a curated public API via `index.ts`, delete the now-dead `@/schemas` barrel re-exports, repoint every external consumer to `@/entities/<name>`, and enforce the entity tier boundaries with eslint.

Behavior must be identical before/after, with the one intended exception the pilot established: schemas are completed to match what the runtime producers actually emit.

Components/hooks/lib that own *planner behavior* (write `usePlannerEditorStore`, encode for save, bucket floor gifts) stay where they are this phase — they belong to `features/planner`, migrated later. Only the entity-display vertical moves.

## Scope (read for context)

- `frontend/src/components/{identity,ego,egoGift,themePack,abEvent,floorTheme}/**`
- `frontend/src/hooks/use{Identity,EGO,EGOGift,ThemePack,AbEvent}*.ts`, `useTraitsI18n.ts`, `usePanicInfo.ts`, `useColorCodes.ts`, `useSearchMappings.ts`
- `frontend/src/types/{Identity,EGO,EGOGift,EGOGiftObservation,ThemePack,AbEvent}Types.ts`
- `frontend/src/schemas/{Identity,EGO,EGOGift,EGOGiftObservation,ThemePack,AbEvent}Schemas.ts` + `schemas/index.ts`
- `frontend/src/lib/{egoGiftSort,egoGiftFilter,egoGiftUtils,EGOGiftObservationData,themePackFilter,abEventFilter,abEventTextResolver,formatSanityCondition,sanityConditionFormatter}.ts`
- `frontend/src/features/extraction/**` (reference slice shape), `frontend/src/features/extraction/index.ts`
- `frontend/eslint.config.js` (lines 27–67 — the boundary blocks to mirror)
- `frontend/CLAUDE.md`, `.claude/rules/frontend/data/schemas.md`
- `docs/tasks/023-fe-feature-first-migration/requirements.md` (the pilot playbook)

## Target (create / modify)

Create (per entity `<name>` ∈ {identity, ego, egoGift, themePack, abEvent}):
```
src/entities/<name>/
├── index.ts            # PUBLIC API — named exports only; NO wildcard re-export of internals
├── components/         # display components (+ their __tests__/), repointed to relative imports
├── hooks/              # entity data/i18n hooks
├── lib/                # entity-owned pure logic (+ __tests__/)  [only where it exists]
├── types/<Noun>Types.ts    # every export = z.infer<typeof Schema>
└── schemas/<Noun>Schemas.ts # completed to match runtime; + __tests__/ safeParse tests
```

Per-entity contents (move these in):

| Entity | components | hooks | lib | types | schemas |
|--------|-----------|-------|-----|-------|---------|
| identity | `components/identity/*` (minus `SkillDescription`,`CoinDisplay`→common) | `useIdentityListData`,`useIdentityDetailData`,`useTraitsI18n`,`usePanicInfo` | `formatSanityCondition`,`sanityConditionFormatter` | `IdentityTypes` | `IdentitySchemas` |
| ego | `components/ego/*` | `useEGOListData`,`useEGODetailData` | — | `EGOTypes` | `EGOSchemas` |
| egoGift | `components/egoGift/*` minus store-writing panes (`ComprehensiveGiftSelectorPane`,`EGOGiftObservationEditPane`,`EGOGiftObservationSelection`,`EGOGiftSelectionList`,`StartGift*`-bound) → stay in `components/` as transitional planner UI | `useEGOGiftListData`,`useEGOGiftDetailData`,`useEGOGiftObservationData` | `egoGiftSort`,`egoGiftFilter`,`egoGiftUtils`,`EGOGiftObservationData` | `EGOGiftTypes`,`EGOGiftObservationTypes` | `EGOGiftSchemas`,`EGOGiftObservationSchemas` |
| themePack | `components/themePack/*` + `ThemePackCard` (moved in from `floorTheme/`) | `useThemePackListData`,`useThemePackDetailData` | `themePackFilter` | `ThemePackTypes` | `ThemePackSchemas` |
| abEvent | `components/abEvent/*` | `useAbEventListData`,`useAbEventDetailData` | `abEventFilter`,`abEventTextResolver` | `AbEventTypes` | `AbEventSchemas` |

> The egoGift store-writing panes are the one judgment call to make per-file at migration time by the mechanical test **"does it import `usePlannerEditorStore`?"** — yes → keep as transitional planner UI (do not move into the entity); no → entity. Confirmed dual-use: `EGOGiftCard`/`EGOGiftList`/`EGOGiftCardLink`/`RecipeSection` = entity; `ComprehensiveGiftSelectorPane`/`EGOGiftObservationEditPane` = planner.

Modify — **Commit 1 (Prep):**
- Move `components/identity/SkillDescription.tsx` + `CoinDisplay.tsx` → `components/common/`; repoint their consumers (`components/ego/EGOSkillCard.tsx:5`, `EGOSkillInfoPanel.tsx:7`, and identity's own skill components).
- Move `components/floorTheme/ThemePackCard.tsx` → `entities/themePack/components/` (created early) or stage in `components/themePack/`; repoint `components/themePack/ThemePackCardLink.tsx`, `routes/ThemePackDetailPage.tsx`.
- `eslint.config.js` — add the entity zones (below).

Modify — **Commit 2 / 3 (repoint sites, verified exhaustive):**
- identity consumers: `deckBuilder/{IdentityGrid,SinnerDeckCard,DeckBuilderContent,DeckBuilderSummary,SinnerGrid}`, `lib/deckFilter`, `routes/{IdentityDetailPage,IdentityPage,EGODetailPage}`.
- ego consumers: `deckBuilder/{CompactEgoGrid,DeckBuilderContent,DeckFilterBar,EgoGrid,SinnerDeckCard,TierLevelSelector}`, `hooks/useHomePageData`, `lib/{deckCode,deckFilter,entitySort}`, `routes/{EGODetailPage,EGOPage}`.
- egoGift consumers: `floorTheme/{FloorGiftSelectorPane,FloorGiftViewer}`, `planner/PlannerMDEditorContent`, `plannerViewer/{ComprehensiveGiftGridTracker,GuideModeViewer,TrackerModeViewer}`, `startBuff/StartBuffMiniCard`, `startGift/{StartGiftRow,StartGiftSummary,StartGiftEditPane}`, `lib/{egoGiftEncoding,floorGiftBucketing,plannerRules,plannerValidation}`, `routes/{AbEventDetailPage,EGOGiftDetailPage,EGOGiftPage,ThemePackDetailPage}`.
- themePack consumers: `routes/{ThemePackPage,ThemePackDetailPage,PlannerMDEditPage}`, `floorTheme/{FloorThemeGiftSection,ThemePackSelectorPane,ThemePackViewer}`, `plannerViewer/{FloorGalleryTracker,ThemePackTrackerCard}`, `hooks/usePlannerSave`, `lib/{plannerRules,plannerValidation}`, `stores/usePlannerEditorStore`.
- abEvent consumers: `routes/{AbEventDetailPage,AbEventPage,ThemePackDetailPage}`, `lib/{abEventFilter,abEventTextResolver}` (move into entity).
- `schemas/index.ts` — delete the entity re-export blocks (lines ~15-21, 23-29, 33-39, 49-51, 75-81, 109-114); repoint any `@/schemas` barrel consumers of those symbols.
- `.claude/rules/frontend/data/schemas.md`, `frontend/CLAUDE.md` — note entity-first location for these five.

Delete (after each move): original `components/<name>/`, the moved `hooks/`, `types/`, `schemas/`, `lib/` files.

### eslint additions (built-in `no-restricted-imports`, mirroring lines 27–67)
- Extend block (c) public-API group: `['@/features/*/**', '@/entities/*/**']` — deep-import ban; reach an entity only via `@/entities/<name>`.
- New block `files: ['src/entities/**']` with two patterns: ban `['@/entities/*','@/entities/*/**']` (sibling-entity ban + forces relative internal imports) and `['@/features/*','@/features/*/**']` (entity ↛ feature).

## Impact Analysis

- **Files modified:** large — 5 entity verticals (~90 component files + ~15 hooks + ~9 lib + 6 type + 6 schema files moved) plus ~45 external repoint sites + barrel + eslint + 2 rule docs. Split across 3 commits keeps each diff reviewable (commit 2 ≈ leaf entities; commit 3 ≈ egoGift/themePack + planner repoints).
- **Dependencies:** routes, `deckBuilder`, `floorTheme`, `planner`, `plannerViewer`, `startBuff`, `startGift`, and several `lib/`/`hooks/` feature files consume these entities — all repoint to `@/entities/<name>` (legal feature/page → entity direction).
- **Ripple:** SSOT conversion may surface type↔schema drift (compiler-enforced); resolve by completing the schema. Barrel removal is safe only after repointing barrel consumers (verify with grep before delete).

## Risk Assessment

- **Edge cases:** SSOT flip surfaces undeclared fields the runtime emits → add to schema, never `as any`. egoGift per-file entity/planner sort depends on the store-import test, not the filename.
- **Performance:** none — structural move + type derivation.
- **Security:** none — no new data boundary; completing schemas can only tighten validation.
- **Bundle (the silent one):** an aggregate barrel would pull Tiptap (via `features/planner → PlannerSchemas`) into a shared/entity chunk while all checks stay green.

## Boundaries & Invariants

- **Trust/ownership boundary:** each `entities/<name>/index.ts` is the only public surface; internals are private (lint-enforced). The entity owns its noun; the planner owns behavior on that noun.
- **Invariant 1 (behavior preservation):** every route renders byte-identically before/after; runtime output unchanged except intended schema completion.
- **Invariant 2 (SSOT):** no entity shape has both a hand-written `interface` and a Zod schema — `type = z.infer<typeof Schema>` for all six type files.
- **Invariant 3 (unidirectional, scoped):** outside code reaches an entity only via `@/entities/<name>`; an entity imports no feature and no sibling entity. (`shared ↛ entities` deferred — feature code still parked in `lib/`/`hooks/`.)
- **Invariant 4 (bundle):** no `src/entities/index.ts` or `src/features/index.ts` aggregate barrel; Tiptap stays out of the main and entity chunks.

## Failure Modes

> Single-developer local refactor — concurrency/interleave rows are N/A. Modes are how the *migration* breaks.

| Invariant | Trigger (how it breaks) | Response | Test |
|-----------|-------------------------|----------|------|
| Inv 1 | An import path missed during a move (partial state) | Build red; `tsc -b` lists every dangling import; fix path | `yarn typecheck` green on each commit |
| Inv 1 | A move step re-run (idempotency) | No-op — files already at destination | `git status` clean after re-run |
| Inv 2 | `z.infer` flip surfaces a field the schema lacks | Add field to schema (it must describe runtime); never cast | `yarn typecheck` + entity `*Schemas.test.ts` |
| Inv 3 | A deep import bypasses the public API, or an entity imports a feature/sibling | `eslint` errors on the banned pattern | `yarn lint` green on real code; errors on injected violation |
| Inv 4 | Someone adds an aggregate barrel + a shared/entity imports it | Tiptap re-enters the main chunk silently; tsc/test/lint stay green | Bundle-size assertion (build) + the forbidden-barrel review check |

### Visualized Failure (worst row: Inv 4, silent bundle regression)
1. A dev adds `src/entities/index.ts` re-exporting all five slices "for convenience."
2. A `components/common` helper imports a type from `@/entities` instead of `@/entities/identity`.
3. That pulls `entities` → (eventually, via a future planner type) `PlannerSchemas` → **Tiptap** into the common chunk.
4. **Broken state:** `yarn typecheck`, `yarn test`, `yarn lint` all pass; the main bundle silently grows by the entire Tiptap editor. "Output is identical" is violated in the one dimension nothing asserts.
   → **Response intervenes:** the no-aggregate-barrel rule (Decision + Inv 4) plus a build-time bundle-size assertion catch it; each slice exposes only its own `index.ts`, so the `@/entities` import target never exists.

## Done When

- [ ] `src/entities/{identity,ego,egoGift,themePack,abEvent}/` each exist with `index.ts` + the segments they need.
- [ ] All moved files deleted from old locations; no dangling imports (`tsc -b` green).
- [ ] Every external consumer imports `@/entities/<name>` (no `@/components/<name>/…`, `@/types/<Noun>Types`, `@/schemas/<Noun>Schemas`, or moved `@/lib/…` references remain).
- [ ] `schemas/index.ts` no longer re-exports the five entities' schemas; no barrel consumer breaks.
- [ ] Every entity type is `z.infer<typeof Schema>`; zero hand-written interface duplicating a schema; schemas completed to runtime.
- [ ] `SkillDescription`+`CoinDisplay` live in `components/common`; `ThemePackCard` lives in `entities/themePack`; `ego` no longer imports `@/components/identity`.
- [ ] eslint entity zones added and **proven**: a deep import into an entity AND an entity→feature import both error; `@/entities/<name>` (index) passes.
- [ ] No `src/entities/index.ts` / `src/features/index.ts` aggregate barrel exists; bundle composition non-regressing.
- [ ] `yarn --cwd frontend typecheck`, `yarn --cwd frontend test`, `yarn --cwd frontend lint` all green — **per commit**.
- [ ] Each of the 3 commits is independently green and behavior-preserving.

## Test Plan

### Test Runner
- Framework: **Vitest** (unit), `tsc -b` (types), `eslint .` (lint) — `frontend/package.json`.
- Commands (redirect to `/tmp/<prefix>-<session-id>-<suffix>.log`; use `--cwd frontend`, never `cd`):
  - `yarn --cwd frontend typecheck`
  - `yarn --cwd frontend test run` (or scoped: `vitest run entities/`)
  - `yarn --cwd frontend lint`

### Tests to Write / Move
- [ ] Move every co-located `__tests__/` with its slice (identity/ego/egoGift/themePack/abEvent component + lib tests); repoint imports to feature-local relative paths; they pass unchanged in logic (proves Inv 1).
- [ ] Per entity, a `schemas/__tests__/<Noun>Schemas.test.ts`: `safeParse` ACCEPTS a valid runtime sample (including any fields added during SSOT completion) and REJECTS an unknown extra key (proves Inv 2 + exercises the schema).
- [ ] Boundary probe (manual, see Verification) realizes the Inv 3 `Test` cell.
- [ ] Bundle-size check realizes the Inv 4 `Test` cell (compare `vite build` output chunk sizes before/after; Tiptap stays only in the planner-loaded chunk).

## Verification

### Manual
1. `yarn --cwd frontend typecheck` → green (SSOT flips + completions compile).
2. `yarn --cwd frontend test run` → green (behavior preserved).
3. Run the app; open `/identity`, `/ego`, `/ego-gift`, `/theme-pack`, `/ab-event` (+ one detail each), and a planner edit/view page that embeds gifts/theme packs — confirm identical rendering.
4. **Boundary probe (both halves):** from a route, temporarily add (a) a DEEP import `@/entities/egoGift/components/EGOGiftCard` → `lint` MUST error; (b) a public-API import `@/entities/egoGift` → `lint` MUST pass. From within `entities/ego`, temporarily import `@/entities/identity` → `lint` MUST error (sibling ban). Revert all.
5. `grep -rn "@/components/\(identity\|ego\|egoGift\|themePack\|abEvent\)/\|@/types/\(Identity\|EGO\|EGOGift\|ThemePack\|AbEvent\)Types\|@/schemas/\(Identity\|EGO\|EGOGift\|ThemePack\|AbEvent\)Schemas" frontend/src` → zero hits outside `entities/`.

### Edge Cases
- [ ] Deep import bypass → eslint errors.
- [ ] Barrel removal → nothing importing `@/schemas` breaks (repoint verified before delete).
- [ ] egoGift store-writing pane misclassified as entity → it would import `usePlannerEditorStore` from inside an entity; caught by the entity↛feature lint rule and the store-import test.

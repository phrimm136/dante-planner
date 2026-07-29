# Task: Migrate `extraction` to a feature-first slice (pilot for FE feature-first architecture)

This is the **pilot** migration of one self-contained leaf feature from the current
**layer-first** layout (`components/`, `hooks/`, `lib/`, `schemas/`, `types/` sliced by technical
layer) to a **feature-first** slice (`features/extraction/` owning all its layers). It doubles as a
learning exercise and a real bug fix (latent type↔schema drift).

It is deliberately scoped to ONE leaf feature. `identity`/`ego`/`egoGift` are shared *entities*
(high cross-feature fan-in) and are explicitly out of scope until the pattern is proven here.

## Decisions

- **Target = `extraction`** — because the dependency graph proves it is a true leaf: external
  fan-in is exactly 1 (its route). `lib/extractionCalculator.ts` is imported by only its own
  component; `types/ExtractionTypes.ts` only by extraction files; `schemas/ExtractionSchemas.ts` by
  *nothing* directly (only re-exported through the `@/schemas` barrel). Zero cross-feature edges →
  the "features cannot import each other" rule holds for free.
- **Layout A: separate `schemas/` + `types/` inside the feature** (not a unified `model/`) — because
  it matches the rest of the app's convention and consumers import the *type*, rarely the schema.
  (Layout B — unified `model/` — was considered and rejected to minimize convention churn during the
  pilot.)
- **SSOT via `z.infer` is the centerpiece, not a side effect** — every extraction type becomes
  `z.infer<typeof Schema>`. This converts silent type↔schema drift into compile errors that force a
  correctness decision. The schema becomes the single source of truth (project rule `schemas.md`).
- **Enforce the architecture with ESLint, not willpower** — `import-x/no-restricted-paths` enforces
  feature↛feature and shared↛feature; built-in `no-restricted-imports` enforces public-API-only. One
  new dev dependency (`eslint-plugin-import-x`). See Enforcement Decision for config + rationale.
- **Route stays in `routes/`** this round — only its import is repointed to the feature's public API.
  Moving routes into an `app/` layer is deferred (out of scope).
- **Learn-by-doing split** — the agent scaffolds the mechanical moves + ESLint rule + doc updates;
  the **user** writes the `z.infer` conversion and resolves each surfaced drift by hand. That step is
  where the learning lives and must not be auto-completed.
- **The feature's `index.ts` is the only public surface** — outside code imports
  `@/features/extraction`, never deep paths into it.

## Description

Move the entire extraction vertical into `src/features/extraction/`, give it a curated public API,
convert its types to schema-derived (`z.infer`), fix the drift the conversion surfaces, repoint the
single external consumer (the route), remove the now-dead barrel re-export, add an enforced import
boundary, and update the two project rule docs that currently mandate layer-first for schemas/types.

Behavior must be **identical** before and after, with one intended exception: the Zod schemas are
completed to match what the calculator actually produces at runtime (they are currently missing
fields). No runtime behavior changes because the schemas are not yet invoked at runtime (wiring
`safeParse` into the input boundary is a deliberate follow-up — see Non-Goals).

## Scope (read for context)

- `frontend/src/components/extraction/{ExtractionCalculator,ExtractionInputs,ExtractionResults}.tsx`
- `frontend/src/lib/extractionCalculator.ts` (+ `lib/__tests__/extractionCalculator.test.ts`)
- `frontend/src/schemas/ExtractionSchemas.ts`, `frontend/src/types/ExtractionTypes.ts`
- `frontend/src/schemas/index.ts` (barrel — re-exports ExtractionSchemas at lines ~101-111)
- `frontend/src/routes/ExtractionPlannerPage.tsx` (the one external consumer; import at line 16)
- `frontend/eslint.config.js` (flat config, no import-boundary plugin yet)
- `frontend/package.json` (scripts: `typecheck`=`tsc -b`, `test`=`vitest`, `lint`=`eslint .`)
- `.claude/rules/frontend/data/schemas.md` (mandates top-level `schemas/`+`types/` — must be amended)
- `frontend/CLAUDE.md` ("Quick Reference" table points at top-level `types/`+`schemas/` paths)
- Reference: bulletproof-react feature shape (`api/ components/ hooks/ stores/ types/ utils/`) and its
  unidirectional rule `shared → features → app`, enforced via `import/no-restricted-paths`.

## Target (create / modify)

Create the slice:

```
src/features/extraction/
├── index.ts                       # PUBLIC API → export { ExtractionCalculator }
├── components/
│   ├── ExtractionCalculator.tsx   # repoint imports: '@/lib/extractionCalculator' → '../lib/extractionCalculator'
│   ├── ExtractionInputs.tsx       #                  '@/types/ExtractionTypes'     → '../types/ExtractionTypes'
│   └── ExtractionResults.tsx
├── lib/
│   ├── extractionCalculator.ts    # repoint type import to '../types/ExtractionTypes'
│   └── __tests__/extractionCalculator.test.ts   # repoint imports to feature-local paths
├── schemas/
│   └── ExtractionSchemas.ts       # complete the schemas to match the calculator's runtime output
└── types/
    └── ExtractionTypes.ts         # SSOT: every shape becomes z.infer<typeof Schema>
```

Modify:
- `src/routes/ExtractionPlannerPage.tsx` — import `{ ExtractionCalculator } from '@/features/extraction'`.
- `src/schemas/index.ts` — DELETE the `ExtractionSchemas` re-export block (no real consumer; verified).
- `frontend/eslint.config.js` — add the boundary rules; install dev dep `eslint-plugin-import-x` (see Enforcement Decision).
- `.claude/rules/frontend/data/schemas.md` — scope the "File Organization" rule to *shared* schemas/types;
  feature-specific ones live in `features/<x>/{schemas,types}`.
- `frontend/CLAUDE.md` — note the feature-first exception in the Quick Reference table.

Delete (after move): the original `components/extraction/`, `lib/extractionCalculator.ts`,
`lib/__tests__/extractionCalculator.test.ts`, `schemas/ExtractionSchemas.ts`, `types/ExtractionTypes.ts`.

### The drift the `z.infer` conversion will surface (full surface, verified)

| Type | Schema | Missing from schema → must add |
|------|--------|--------------------------------|
| `ExtractionInput` | `ExtractionInputSchema` | `featuredAnnouncerCount: number` (calc uses it at `extractionCalculator.ts:806`) |
| `ExtractionResult` | `ExtractionResultSchema` | `allTargetProbability`, `successiveProbabilities`, `totalItemsWanted`, `pityCount` |
| `SuccessiveProbability` | *(no schema exists)* | create `SuccessiveProbabilitySchema` so `ExtractionResult` can reference it |
| `BannerModifiers`, `ExtractionTarget`, `TargetProbability`, `EffectiveRates` | matching schemas | already aligned — no change |

The calculator (`lib/extractionCalculator.ts`) is the source of runtime truth for the result shape;
the schema is amended to match it, then `type = z.infer<...>`.

## Impact Analysis

- **Files modified:** ~13 (3 components, 1 calc + test, schema, types, schema barrel, route, eslint,
  2 rule docs, new index.ts). Blast radius is contained to the extraction vertical + 4 cross-cutting
  files (route, barrel, eslint, docs).
- **Dependencies / ripple:** the only runtime external consumer is `ExtractionPlannerPage.tsx`.
  Barrel deletion is safe — verified all **8** extraction schema symbols (`ExtractionInputSchema`,
  `ExtractionResultSchema`, `ExtractionTargetSchema`, `BannerModifiersSchema`, `TargetProbabilitySchema`,
  `EffectiveRatesSchema`, `ExtractionTargetTypeSchema`, `ActiveRateTableSchema`) have **zero** consumers
  outside the extraction vertical, and no `types/index.ts` barrel exists to re-export `ExtractionTypes`.
  `tsc -b` is the backstop if any reference was missed.
- **Shared deps stay shared (legal `features → shared` direction):** `@/components/ui/*`,
  `@/components/common/PlannerSection`, `@/lib/constants` (`EXTRACTION_RATES`, `SECTION_STYLES`),
  `@/lib/utils` (`cn`). The feature imports these; none move.

## Risk Assessment

- **Edge cases:** the `z.infer` flip may surface MORE drift than the table above if the calculator
  reads fields not yet enumerated — the compiler is the authority; resolve whatever it flags.
- **Anti-pattern to forbid during the fix:** "fixing" a surfaced compile error with `as any` /
  casting re-buries the drift. The only correct resolution is to add the missing fields to the schema.
- **Performance:** none — pure structural move + type derivation.
- **Security:** none — no new data boundary, no user-input handling changes.

## Boundaries & Invariants

- **Trust/ownership boundary:** `features/extraction/index.ts` is the public API. Everything else in
  the slice is private; no external file may import a deep path into the feature.
- **Invariant 1 (behavior preservation):** the calculator's observable output is byte-identical
  before and after — this is a refactor, not a rewrite.
- **Invariant 2 (single source of truth):** after migration, no extraction shape has both a
  hand-written `interface` and a Zod schema. For every shape, `type = z.infer<typeof Schema>`.
- **Invariant 3 (unidirectional boundary):** `features/extraction` imports from shared layers only,
  never from another feature; shared layers (`lib/`, `components/`, `schemas/`, `types/`) import
  nothing from `features/`; external code reaches the feature only via `index.ts`.
  NOTE: all three clauses are lint-enforced — (a)+(b) via `import-x/no-restricted-paths`, (c) via
  built-in `no-restricted-imports` (`@/features/*/**` glob). See Enforcement Decision.

## Failure Modes

> Concurrency rows are N/A: this is a single-developer local refactor with no runtime state machine.
> Listed modes are the ways the *migration* can break, each with a test that proves the response.

| Invariant | Trigger (how it breaks) | Response | Test |
|-----------|-------------------------|----------|------|
| Inv 1 | An import path missed during the move | Build goes red; fix the path | `tsc -b` + `vitest run extractionCalculator` |
| Inv 1 | Migration applied half-way (files moved, imports not all updated) — partial state | Land as ONE atomic commit; `tsc` lists every remaining broken import (self-healing via compiler) | `tsc -b` must be green on the commit |
| Inv 1 | Re-running a completed move step (idempotency) | No-op — files already at destination; step is safe to re-run | `git status` clean after re-run |
| Inv 2 | `z.infer` flip surfaces fields the schema lacks (`featuredAnnouncerCount` + 4 result fields) | Add the missing fields + `SuccessiveProbabilitySchema` to match the calculator | `tsc -b` + existing `extractionCalculator.test.ts` (asserts the produced shape) |
| Inv 3 | ESLint zones misconfigured → blocks legal `feature→shared` OR fails to block `feature→feature` | Prove the rule with a deliberate violation, then revert | `eslint .` clean on real code; lint ERRORS on the injected violation |

### Visualized Failure (worst row: Inv 2, result-shape drift)

1. User flips `type ExtractionResult` to `z.infer<typeof ExtractionResultSchema>`.
2. `ExtractionResults.tsx` renders `result.successiveProbabilities` — but the schema never declared it,
   so the inferred type lacks the field.
3. `tsc -b` errors: *Property 'successiveProbabilities' does not exist on type 'ExtractionResult'.*
4. **Broken state reached.** The tempting wrong fix is `(result as any).successiveProbabilities` —
   which re-buries the drift permanently.
   → **Response intervenes here:** the correct resolution is to create `SuccessiveProbabilitySchema`
   and add the 4 missing fields to `ExtractionResultSchema`, because the schema must describe what the
   calculator actually returns. The compiler refusing the incomplete schema is exactly what SSOT buys.

## Project-Convention Sections (from `docs/spec.md`)

**N/A — extraction is not a data-driven feature.** It consumes user input + rate constants
(`EXTRACTION_RATES`), not raw `static/data/*.json` game files. The Data Model Catalog, Normalization
Layer, Rendering Mode Enumeration, Reference Per Mode, and pipeline Implementation Order do not apply.

## Enforcement Decision (RESOLVED)

**Tool: `import-x/no-restricted-paths` (load-bearing rules) + built-in `no-restricted-imports`
(public-API rule).** All three clauses of Invariant 3 are lint-enforced; one new dev dependency.

Why, grounded in the reference's *actual* config (pulled, not recalled):
- bulletproof-react's real `.eslintrc.cjs` uses `import/no-restricted-paths` zones for exactly
  (a) feature↛feature and (b) shared↛feature — and does **not** lint-enforce public-API/index-only
  (its `except` field only lets a feature import itself). We adopt its load-bearing approach.
- `import/no-restricted-paths` keeps all zones in **one rule**, avoiding the flat-config trap where
  the same rule in overlapping `files` blocks *overrides* instead of merging.
- Clause (c) "public-API only" is enforced cheaply by the BUILT-IN `no-restricted-imports` (a
  *different* rule, so no override conflict) with glob `@/features/*/**` — bans every deep path while
  allowing the bare `@/features/<name>` (→ `index.ts`). This is the encapsulation the reference leaves
  to convention, at zero extra dependency.
- `eslint-plugin-boundaries` rejected for the pilot: its element-type model is more config ceremony
  than one feature warrants. It's the upgrade path if declarative multi-feature governance is later needed.

Config (add to `frontend/eslint.config.js`):
```js
import importX from 'eslint-plugin-import-x'

{
  files: ['**/*.{ts,tsx}'],
  plugins: { 'import-x': importX },
  settings: { 'import-x/resolver': { typescript: true } },
  rules: {
    'import-x/no-restricted-paths': ['error', { zones: [
      // (a) a feature cannot import sibling features (except itself)
      { target: './src/features/extraction', from: './src/features', except: ['./extraction'] },
      // (b) shared layers cannot import features
      { target: ['./src/components','./src/hooks','./src/lib','./src/schemas','./src/types','./src/stores'],
        from: './src/features' },
    ]}],
    // (c) public-API only — deep imports banned, bare '@/features/<name>' allowed
    'no-restricted-imports': ['error', { patterns: [{
      group: ['@/features/*/**'],
      message: 'Import a feature only via its public API: @/features/<name>',
    }]}],
  },
},
```
Depends on the feature using RELATIVE internal imports (`../lib/...`) so the `@/features/*/**` glob
(which only matches `@/`-aliased strings) never trips on the feature's own code — already in Target.

Implementation detail to confirm at install (NOT an architecture decision): `eslint-plugin-import-x`
is the maintained flat-config/TS fork (rule prefix `import-x/`). If you prefer original
`eslint-plugin-import`, the rule is `import/no-restricted-paths` + add `eslint-import-resolver-typescript`.

## Done When

- [ ] `src/features/extraction/` exists with `index.ts`, `components/`, `lib/`, `schemas/`, `types/`.
- [ ] All five original files moved; old locations deleted; no dangling imports.
- [ ] `ExtractionPlannerPage.tsx` imports `ExtractionCalculator` from `@/features/extraction`.
- [ ] `schemas/index.ts` no longer re-exports `ExtractionSchemas`.
- [ ] `types/ExtractionTypes.ts`: every exported type is `z.infer<typeof Schema>` — zero hand-written
      interfaces duplicating a schema.
- [ ] `ExtractionInputSchema` includes `featuredAnnouncerCount`; `ExtractionResultSchema` includes the
      4 missing fields; `SuccessiveProbabilitySchema` exists.
- [ ] ESLint boundary rules added and **proven** — a deep import into the feature AND a cross-feature
      import both error; a public-API (`@/features/extraction`) import passes.
- [ ] `.claude/rules/frontend/data/schemas.md` + `frontend/CLAUDE.md` updated for the feature-first exception.
- [ ] `yarn --cwd frontend typecheck`, `yarn --cwd frontend test`, `yarn --cwd frontend lint` all pass.
- [ ] No behavior change beyond the intended schema completion.

## Test Plan

### Test Runner
- Framework: **Vitest** (unit), `tsc -b` (types), `eslint .` (lint) — per `frontend/package.json`.
- Commands (redirect output to `/tmp/<prefix>-<session-id>-<suffix>.log` per project convention; use
  `--cwd frontend`, never `cd`):
  - `yarn --cwd frontend typecheck`
  - `yarn --cwd frontend test run extractionCalculator`
  - `yarn --cwd frontend lint`

### Tests to Write
- [ ] Move `extractionCalculator.test.ts` to `features/extraction/lib/__tests__/`; repoint its imports;
      it must pass unchanged in logic (proves Inv 1 — behavior preserved).
- [ ] NEW `features/extraction/schemas/__tests__/ExtractionSchemas.test.ts`: `ExtractionInputSchema.safeParse`
      ACCEPTS a valid input that includes `featuredAnnouncerCount`, and REJECTS an unknown extra key
      (proves the drift fix + that `.strict()` still holds). This also makes the schema actually exercised.
- [ ] Boundary probe (manual, see Verification): a temporary cross-feature import must trip ESLint.

## Verification

### Manual
1. `yarn --cwd frontend typecheck` → green (proves the `z.infer` flip + schema completion compile).
2. `yarn --cwd frontend test run extractionCalculator` → green (behavior preserved).
3. Run the app, open `/planner/extraction`, enter inputs, confirm probabilities render identically.
4. **Boundary probe (both halves):** from some *other* component, temporarily add (a) a DEEP import
   `from '@/features/extraction/lib/extractionCalculator'` → `lint` MUST error; and (b) a LEGAL import
   `from '@/features/extraction'` (via index) → `lint` MUST pass. If (b) also errors, the rule is too
   strict; if (a) passes, the `@/features/*/**` glob is misconfigured. Revert both.
5. `grep -rn "from '@/lib/extractionCalculator'\|from '@/types/ExtractionTypes'\|from '@/schemas/ExtractionSchemas'"`
   → zero hits (all consumers repointed).

### Edge Cases
- [ ] Deep import bypass: `import x from '@/features/extraction/components/...'` from outside → ESLint errors.
- [ ] Barrel removal: nothing that imports `@/schemas` breaks (extraction exports had no real consumers).

## Non-Goals (explicit follow-ups, not this task)

- Wiring `ExtractionInputSchema.safeParse` into the live form-input boundary (kills the "decorative
  schema" smell) — separate task once the slice lands.
- Migrating `identity`/`ego`/`egoGift` (shared entities — need the `entities/` layer decision first).
- Moving routes into an `app/` layer.
- Generalizing the ESLint zones to all future features (the pilot proves it for one).

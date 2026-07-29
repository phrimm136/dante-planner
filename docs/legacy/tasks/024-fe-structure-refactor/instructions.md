# Task: Frontend Structure Refactor — Consolidation & Boundary Enforcement

## Decisions

- **Type↔Schema direction for Planner** (evidence: saved-data versioning across contentVersion deployments) — Planner's `.strict()` Zod gate must reject no valid *yesterday's* saves even when *today's* type changes. Deriving schema from type collapses schema evolution to "current version only" and silently destroys old saves on load. Mitigation: keep both artifacts; pin their relationship with type-level assertions (`Expect<Equal>` on leaves, one-directional `extends z.input` on composites). Generation rejected.
- **Consolidate patterns before relocating structure** (taste) — Because refactoring mechanical duplication first leaves fewer moving parts for the file-move phase. Moves stay once; patterns don't yet stabilize if moved early.
- **Four-layer FE model: pages | shared/<concept> | components | lib** (taste) — Ownership-driven placement (pages own slices; shared owns vertical concepts co-used across pages; components are React-native; lib is domain-free). Replaces horizontal layers that cut across ownership boundaries.
- **Explicitly refuse generic `FilterPageShell`** (user scope) — Single-use abstractions create indirection without reuse. Leaves are cheaper than misdirection.
- **Hook hardens test-directory enforcement** (evidence: stray `node_modules/.vite` cache in repo root from vitest run without `--cwd`) — Prevent cache/config pollution by requiring FE commands (`--cwd frontend`) and BE commands (`-p backend` / `-C backend/`) in the hook itself, not in documentation. Violations exit 2 before redirect checks.
- **BE refactoring deferred to parallel session** (workflow) — Another session owns semantic refactors (B1–B11 invariant placement, controller split) and repackaging. Keeps scope focused and allows parallel progress. Both sessions share Boundary enforcement rules post-completion.

## Description

Eliminate horizontal duplication in the shared layer and mechanical repetition in the data-access layer. Consolidate 12 hooks and 34 query-key factories into two factory functions; extract duplicate validation, utility functions, and React hooks; enforce ownership-based file placement via eslint boundaries and a four-layer architectural model.

Refactor is FE-only. Backend refactoring is a separate workstream.

## Scope

**Read for context:**
- `frontend/src/` — all current structure and existing patterns
- `.claude/rules/frontend/` — current architecture rules
- `.claude/hooks/check-output-redirect.sh` — test-running hook
- `CLAUDE.md` — page-slice model definition, import conventions
- `frontend/CLAUDE.md` — FE-specific rules (if exists; check)
- `.eslintrc` — current boundary rules

## Target

**Create or Modify:**
1. `frontend/src/lib/` — consolidate 11 domain-free utilities
2. `frontend/src/shared/<concept>/` — four vertical concepts (skill, gameText, gameData, assets, filter, comment, notifications, auth, sse, noteEditor, moderation)
3. `frontend/src/pages/<slice>/` — slice-specific components and hooks, migrate from current structure
4. `frontend/src/components/` — ui, icons, layout, feedback only (remove horizontal layers)
5. `.claude/rules/frontend/` — update architecture/testing docs to reflect new tree
6. `.claude/hooks/check-output-redirect.sh` — harden with project-directory checks
7. `CLAUDE.md` — update rule 11 (test-directory requirement), expand target for FE refactor
8. `frontend/CLAUDE.md` — new file if missing; consolidate FE-specific conventions

**Delete:**
- `frontend/src/routes/` — old flat route structure (replaced by page-slice router imports)
- `frontend/src/components/common/` — contents redistributed to shared or components/
- Root `frontend/src/hooks/`, `frontend/src/schemas/`, `frontend/src/types/`, `frontend/src/stores/` — contents migrated to pages or shared
- `frontend/src/App.tsx`, `frontend/src/App.css`, `frontend/src/routeTree.gen.ts` — if unused
- All `.bak` files in frontend/
- Empty directories after migration

**Sync (Phase 5 only):**
- `CLAUDE.md` — update Quick Reference row for page-slice boundary
- `.claude/rules/` — all docs rewritten against final tree
- `frontend/CLAUDE.md` — finalized

## Invariants

- **INV1: Behavior byte-identical** — REST paths, request/response bodies, DB schema, URL routes, lazy-load boundaries unchanged. Test: `./gradlew test` (BE), `yarn --cwd frontend vitest` (FE), `yarn --cwd frontend tsc -b` (type check) pass at every phase boundary. ✓ Existing suite validates.

- **INV2: No deep imports outside router.tsx** — No file outside `frontend/src/lib/router.tsx` imports from `@/pages/*/**` (i.e., into a slice's internal structure). Test: `grep -rn "@/pages/[^/]*/\(components\|hooks\)" frontend/src --exclude-dir=node_modules --exclude='router.tsx'` must return empty after each phase. ✓ Enforced by eslint boundary rule.

- **INV3: Planner schema drift guard compiles** — Type and schema agreement verified by `Expect<Equal>` on leaf types and one-directional `extends z.input` on composites. Test: `yarn --cwd frontend tsc -b` exits 0; any type/schema divergence fails compile. ✓ No runtime check needed; type system validates.

- **INV4: Bundle size unchanged** — Tiptap (large editor library) does not appear in entry chunk. Test: inspect `tsc` output and Vite bundle manifest (existing check, if any). ✓ Validate via existing build checks or add static assertion in bundle config if missing.

- **INV5: Query and validation factories collapse duplication** — 12 hooks and 34 key factories reduced to 2 factories + 1 validation helper. Test: grep count of original factories vs. final (exact count verification via pattern search). ✓ Post-consolidation code inspection.

- **INV6: Boundary rule sets execute in CI** — eslint deep-import ban and (post-BE phase) ArchUnit cross-feature edge allowlist run in CI before merge. Test: CI workflow includes `yarn --cwd frontend lint` + (backend if enabled) `./gradlew check`. ✓ Existing CI gates validate.

## Done When

- [ ] **Phase 1 (Hygiene)** — All preliminary deletes and hook updates complete
  - [ ] `node_modules/` entry added to `.gitignore` (if missing)
  - [ ] `network-ts-requests.log`, root screenshots, `App.tsx/App.css`, `routeTree.gen.ts`, commented router plugin block, `routes/` directory, all `.bak` files, empty dirs removed
  - [ ] Hook updated with project-directory blocking checks (`--cwd frontend` for FE, `-p backend` for BE); tests pass
  - [ ] `.claude/rules/frontend/testing.md` documents canonical test commands
  - [ ] `CLAUDE.md` rule 11 extended; `backend/CLAUDE.md` build-tool line updated

- [ ] **Phase 2 (Pattern Consolidation)** — Factories and utilities extracted, Planner drift guard in place
  - [ ] `createStaticDataQueryOptions` and `createEntityQueryKeys` factories collapse 12 hooks and 34 key factories
  - [ ] `lib/validation.ts` (`validateData`) created and adopted at 25+ validation sites
  - [ ] `areSetsEqual`, `useSetFilters`, `useProgressiveReveal` (existing) integration complete
  - [ ] Mutation-skeleton and dropdown-option helpers extracted
  - [ ] Keyword, StartBuff, ColorCode, Announcement, Comment, Notification, UserSettings converted to `z.infer`
  - [ ] Planner assertion guards (`Expect<Equal>` leaves, one-directional `extends z.input` composites) compile; type check passes

- [ ] **Phase 3 (Structure Collapse)** — Files moved to four-layer model, imports rewritten
  - [ ] `frontend/src/lib/` consolidated to ~11 domain-free files
  - [ ] `frontend/src/shared/<concept>/` populated with vertical modules (skill, gameText, gameData, assets, filter, comment, notifications, auth, sse, noteEditor, moderation)
  - [ ] Page slices migrated to `frontend/src/pages/<slice>/` with relative intra-slice imports
  - [ ] `frontend/src/components/` contains only ui, icons, layout, feedback
  - [ ] All intra-slice imports rewritten to relative paths; cross-slice imports remain `@/...`
  - [ ] `frontend/src/lib/router.tsx` lazy-import paths updated; only `router.tsx` exempted from boundary rule
  - [ ] `git diff` each moved file shows ONLY import lines changed (no body drift)
  - [ ] Deep-import grep (`@/pages/*/**` outside `router.tsx`) returns empty
  - [ ] `yarn --cwd frontend tsc -b` + `yarn --cwd frontend vitest` pass; existing tests green

- [ ] **Phase 4 (FE Boundary Rules)** — eslint deep-ban extended to block shared/* cross-contamination
  - [ ] eslint config extended: `no-restricted-imports` rule bans `@/shared/*/**` from files outside that shared module
  - [ ] Shared→Pages sink rule configured (shared can import pages only via public barrel, if any)
  - [ ] `yarn --cwd frontend lint` passes; no new violations
  - [ ] Boundary validation in CI confirmed active

- [ ] **Phase 5 (Doc Truth-Sync)** — CLAUDE.md, `.claude/rules/`, Quick References updated to final tree
  - [ ] `CLAUDE.md` page-slice Quick Reference row rewritten with actual paths and current layout
  - [ ] `.claude/rules/frontend/architecture.md` and other rule files updated against new structure
  - [ ] `frontend/CLAUDE.md` finalized (or confirmed) with FE-specific conventions
  - [ ] All path references in docs point to files that exist post-refactor
  - [ ] No "before" state language remains in docs

- [ ] **All phases: Consistency verified**
  - [ ] `./gradlew test` (BE) passes
  - [ ] `yarn --cwd frontend vitest` (FE) passes
  - [ ] `yarn --cwd frontend tsc -b` (type check) passes
  - [ ] `yarn --cwd frontend lint` (eslint) passes
  - [ ] All invariants hold post-completion

## Test Plan

### Test Runner
- **Frontend:** `yarn --cwd frontend vitest` (test framework: vitest + jsdom)
- **Type check:** `yarn --cwd frontend tsc -b` (TypeScript compilation)
- **Linting:** `yarn --cwd frontend lint` (eslint with boundary rules)
- **Backend:** `./gradlew -p backend test` (for cross-layer consistency checks, if needed)

### Tests to Write

> Consolidation and structure changes are refactorings — behavior is byte-identical, so **no new test cases are needed.** Existing test suite must pass at every phase boundary. Verify via command above.

### Tests to Update

- [ ] **Phase 2:** Repoint `vi.mock` paths in moved tests to new locations (e.g., `@/components/home/X` → `@/pages/home/components/X`). Stale mocks silently stop intercepting. Grep for `vi.mock` in test files and audit paths.
- [ ] **Phase 3:** After structure moves, grep for old import paths (`@/hooks/`, `@/schemas/`, `@/components/common/`) in test files and rewrite to new locations.
- [ ] **INV3 (Planner Drift Guard):** Already covered by type check (`tsc -b`); no new test file needed.

### Invariant Verification

Each invariant is verified via existing tooling:
- **INV1** (byte-identical): `./gradlew test` + `yarn --cwd frontend vitest` + `yarn --cwd frontend tsc -b`
- **INV2** (no deep imports): `grep -rn "@/pages/[^/]*/\(components\|hooks\)" frontend/src --exclude-dir=node_modules --exclude='router.tsx'` + eslint boundary rule in CI
- **INV3** (Planner drift guard): `yarn --cwd frontend tsc -b` (compile-time validation)
- **INV4** (bundle size): Existing build checks (add if missing)
- **INV5** (factory count): Manual code inspection post-consolidation
- **INV6** (CI gates): CI workflow runs eslint + tsc before merge

### Execution Sequence

1. **Before Phase 1:** Commit current state (clean tree).
2. **After each phase boundary (1→2, 2→3, 3→4, 4→5, 5→done):**
   ```bash
   ./gradlew -p backend test
   yarn --cwd frontend vitest
   yarn --cwd frontend tsc -b
   yarn --cwd frontend lint
   ```
3. If any step fails, stop and diagnose before proceeding.
4. **Post-completion:** Run full suite and verify all invariants.

---

## Implementation Notes

- **Phases 0–1:** Already executed or in progress (lineage-rotation committed; node_modules/.vite removed; hook updated).
- **Phases 2–5:** Sequential; each depends on the prior. Do not parallelize.
- **BE work:** Out of scope; handled by parallel session. Both sessions align on boundary enforcement rules post-completion.
- **Memory:** Stale memory entries (BE refactor status, transitional egoGift panes) will be corrected at wrap time.

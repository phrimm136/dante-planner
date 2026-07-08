# Results: FE app-shell & utility pages migration

## What Was Done
- Migrated 8 route components out of `src/routes/` into the flat page-slice model, eliminating the `routes/` ↔ `components/<name>/` split-brain.
- Created 5 page slices (no `index.ts`, none externally consumed): `pages/home/`, `pages/settings/`, `pages/keyword/`, `pages/moderator/`, `pages/legal/`.
- Relocated `NotFoundPage` to `components/common/` (it is `defaultNotFoundComponent`, sibling to the other fallback components).
- Extracted `pages/legal/components/LegalPage.tsx` (the only new code); Privacy/Terms are now thin wrappers parameterized by `{ namespace, lastUpdated, sections, bulletSections }`.
- Repointed all 8 lazy/eager imports in `router.tsx`; route paths, meta, i18n keys, and the keyword loader unchanged.

## Files Changed
- `src/lib/router.tsx` — 8 import paths repointed
- `src/components/common/{ErrorBoundary,RouteErrorComponent}.tsx` — NotFoundPage import → `./NotFoundPage`
- `src/components/common/NotFoundPage.tsx` — moved from `routes/`
- `src/components/common/CommunityPlansErrorFallback.tsx` (+ test) — moved from `components/home/` (dual-consumer)
- `src/pages/legal/{LegalPage(new),PrivacyPage,TermsPage}.tsx`
- `src/pages/home/**` (HomePage + 7 components + useHomePageData + tests)
- `src/pages/settings/**` (SettingsPage + 7 components + tests)
- `src/pages/keyword/**` (2 routes + 4 components)
- `src/pages/moderator/**` (ModeratorPage + 2 hooks + schemas + types; BanDialog NOT moved)
- `src/pages/planner/PlannerMDGesellschaft{,Detail}Page.tsx` — CommunityPlansErrorFallback import repointed

## Verification
- Build (`tsc -b`): PASS (exit 0)
- Tests (`vitest run`): PASS — 119 files, 6107 passed, 1 skipped
- Lint (`eslint .`): COULD NOT RUN — pre-existing env breakage (`await-thenable` typed rule lacks `parserOptions.project`, crashes on untouched `App.tsx`). Boundary invariant verified by grep instead: zero deep `@/pages/*/**` import statements outside `router.tsx`.
- Manual/empirical: `LegalPage` render parity confirmed via throwaway RTL test (privacy 7 sections/2 bullet lists/mailto; terms 7/1/mailto), then removed.
- Review: single `code-architecture-reviewer` → ACCEPTABLE; 3 Minor findings, all pre-existing or scope-respecting.

## Issues & Resolutions
- `CommunityPlansErrorFallback` consumed by 2 planner pages (spec assumed home-owned) → relocated to `components/common/` per dual-consumer rule (grep caught it after the move broke tsc).
- `BanDialog` exports `CommentDeleteDialog` consumed by `components/comment/CommentSection` (spec said move into slice) → left in `components/moderation/`; ModeratorPage imports it from the shared path unchanged.
- `git mv` needs the destination dir to exist → `mkdir -p` each slice dir first.
- `tsc -b` after each phase isolated the dual-consumer break to exactly the phase that introduced it.

## Learnings
- **Grep all of `src` for external consumers BEFORE moving a component into a slice.** The spec's per-page research said home/moderator components were "self-contained"; two were not (`CommunityPlansErrorFallback`, `BanDialog`). A `git mv` + `tsc -b` + full-src grep per move caught both. The dual-consumer rule ("consumed by 2+ domains stays shared, never moves into a consuming slice") is the decision procedure.
- **Execute mechanical file moves directly (`git mv` + import-only `Edit`s), not via code-writer agents.** Agents Read→Write and risk content drift/reformatting on a pure relocation. `git status` showing `R`/`RM` (not `D`+`??`) is the proof of a faithful move; `git diff` on each should show only import lines.
- **Intra-slice imports must be RELATIVE** (`./components/X`), because eslint `no-restricted-imports` bans `@/pages/*/**` even from a slice's own files. The bare `@/pages/<name>` barrel is allowed but only exists if the slice has an `index.ts`.
- **`vi.mock` paths must be repointed to the moved module location.** `vi.mock('@/components/home/X')` silently stops intercepting once the code-under-test imports `./components/X` from a new dir; repoint to `@/pages/home/components/X`. (These are string args, NOT import statements, so `no-restricted-imports` does not flag them.)
- **`LegalPage` `bulletSections.includes(section)`** cleanly generalizes the two originals' hardcoded conditions; `useTranslation()` defaults to the `common` namespace where `pages.privacy.*`/`pages.terms.*` live.

## Spec Divergence

### What Changed
- `CommunityPlansErrorFallback` destination: spec → `stays in components/home` implicitly (listed under home's owned components); actual → `components/common/` because 2 planner pages consume it.
- `BanDialog` disposition: spec Target listed it moving to `pages/moderator/components/`; actual → stays in `components/moderation/` (consumed by `CommentSection`).

### What Was Added (Not in Spec)
- Nothing functional. The throwaway `LegalPage` render check was added then removed (honored "no new tests").

### What Was Dropped
- `yarn lint` gate from the Done-When list — not dropped by choice; the lint tooling is pre-existingly broken in this env. Substituted a grep-based boundary check.

### Wrong Assumptions
- Spec §Risk/Impact: "each candidate page's `components/<name>/` is NOT used across page boundaries (each owns its UI components)." FALSE for `CommunityPlansErrorFallback` (home+planner) and `BanDialog` (moderator+comment). The spec's stay-untouched list should have included both.

### Prompting Retrospective
- **Cross-consumer discovery**: "For each component under `components/<name>/` I plan to move, grep all of `src` for importers outside that page — list every dual-consumer before deciding what moves." Why: would have surfaced `CommunityPlansErrorFallback` and `BanDialog` at spec time instead of mid-build, avoiding a tsc break and an in-flight relocation.
- **Verification tooling reality-check**: "Confirm `yarn lint` and `yarn tsc -b` actually run green in this environment on an untouched file before listing them as Done-When gates." Why: would have flagged the pre-existing eslint config breakage up front rather than at the final gate.

### Spec Process Takeaway
This spec systematically under-counted **shared-module fan-out**: its per-page research traced what each page *imports* but not who *imports each page's components*, so dual-consumers (the exact thing the page-slice boundary cares about) were missed until the compiler forced them into view.

## Session State (for continuity)
- **Status**: COMPLETE and reviewed (ACCEPTABLE). Nothing committed — changes are staged renames + 1 new file (`pages/legal/components/LegalPage.tsx`) in the working tree.
- **Uncommitted**: 42 renames, 18 rename+modify/modify, 1 new dir (`pages/legal/components/`). (`components/noteEditor/extensions/__tests__/` untracked is pre-existing, not this work.)
- **Next steps**: (1) commit via `commit-process`; (2) optional: lift `CONTACT_EMAIL` to `constants.ts` + use in `LegalPage`+`Footer` (review finding #1, its own change); (3) optional: fold the two dual-consumer corrections into the spec's stay-untouched list.
- **Blockers**: none.

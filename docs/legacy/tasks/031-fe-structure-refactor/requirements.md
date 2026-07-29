# Task: FE Structure Refactor — Pattern Consolidation, Four-Layer Collapse, Boundary Enforcement

FE-only. Backend refactoring (semantic refactors, package-by-feature, ArchUnit edges) is owned by a parallel session and is OUT of this spec's scope.

Note: `docs/024-fe-structure-refactor/` is a superseded draft of this spec written by another session; it misstates the planner gate as `.strict()` and the gradle flag as `-C`. This document is authoritative.

## Decisions

- FE-only scope — BE workstreams dropped from this task (user decision; another session owns them).
- **Consolidate patterns BEFORE relocating files** — moving 12 near-identical hooks into 6 slices first means fixing duplication across 6 directories later; consolidate, then relocate the survivors (evidence: `useIdentityListData`/`useEGOListData`/`useEGOGiftListData` are 113 lines, 95% identical).
- **Four-layer model** (taste): directories are named by what code *is about*, never by how much it is reused. Reuse-degree names (`common`, `util`, `misc`) always converge to landfills because reuse changes silently while domain changes only with the domain. Layers: `pages/<slice>` (routed domains) | `shared/<concept>` (co-owned domain concepts, vertical) | `components/` (domain-free React UI kit) | `lib/` (domain-free non-React infra).
- **Ownership over usage-count** (taste): fan-in is evidence for domain code but noise for infrastructure. Domain code with one owner lives in that owner; domain code with no single owner gets a *named* shared concept; infrastructure stays in `lib/`/`components/` regardless of consumer count (`uuid` with 1 consumer stays `lib/`).
- **Co-owned components = headless core + per-page adapters** (taste): when every consumer has equal claim (skill card ↔ identity/ego/enemy), the rendering core is owned by the concept (zero feature imports — the observable invariant of healthy sharing) and each page owns its data adapter. A `variant`/`isEgo` branch in the core is the chimera signal to split back.
- **`components/` keeps its name despite containing `hooks/`** (evidence: `components/ui` path pinned by shadcn `components.json`; 136+ import sites). Nominal purity loses to three-digit-file churn.
- **Parse-boundary types derive via `z.infer` — including API responses; planner exempt** (evidence: double bookkeeping already exists — `BattleKeywordSpecEntry` interface + `BattleKeywordsSchemas` describe the same pipeline JSON with nothing forcing agreement, and Zod `z.object` strips undeclared fields silently). Planner stays hand-written: its gate is deliberately *looser* than its type (`content: z.record(string, unknown)` for two-step validation; a strict gate would discard whole planners on load — blast-radius invariant), and reader-schema must accept older saves than writer-type describes. Type→schema codegen (ts-to-zod/typia) rejected for the same reason: generators can only express equality, the one relationship the planner forbids. Consistency is pinned by an assertion drift guard instead: `Expect<Equal<z.infer<S>, T>>` for leaf types, one-directional `T extends z.input<typeof S>` for composites.
- **Refuse a generic `FilterPageShell`** (taste): extract the organs (filter-state hook, progressive reveal, predicates), keep page skeletons page-owned. Structure-varying code is copied; only data-varying code is parameterized.
- **`constants.ts` / `assetPaths.ts` split as codemod phase** (default): 179 / 75 import sites; re-export shim at old path → flip imports mechanically → delete shim, `tsc -b` gating each step. Game vocabulary → `shared/gameData`, asset path grammar → `shared/assets`; domain-free remainder stays `lib/`.
- **User placements**: `CommunityPlansErrorFallback` → `components/feedback` (accepted domain-flavored exception; chimera-watch), `StarlightCostDisplay` + `SectionNoteDialog` → `pages/planner`, `SyncChoiceDialog` + `useFirstLoginStore` → `pages/settings` (GlobalLayout borrows via `@/pages/settings` bare barrel — legal, tree-shakeable named exports keep the bundle invariant), icons → `components/ui`.
- **`egoGiftEncoding` owner = `pages/egoGift`** (weak call): the format encodes egoGift IDs — the format-definer owns; planner (7 consumers) borrows via public API.
- **`formatUsername` → `shared/auth`** (low confidence; `lib/` acceptable fallback): encodes the epithet·suffix user-domain rule.
- **Keep `filter/` name** for the browsing-controls concept (default: rename to `catalog/` is cosmetic churn).
- **Boundary-only flat eslint config as the CI lint gate** (evidence: full config's `strictTypeChecked` lacks `parserOptions.projectService` and throws at load; wiring it surfaces ~1,403 latent errors — separate cleanup). The structural rules must *execute*, or the architecture is a comment.
- **2-digit docs numbering** (default: repo series `24…30`; skill's 3-digit auto-numbering not used).

## Description

Complete the FE's vertical-module architecture: collapse the pre-slice horizontal shared layer (`components/common`, root `hooks/ lib/ schemas/ types/ stores/ routes/`) into the four-layer model, eliminate mechanical duplication in the data layer (~1,200 lines across hooks/key-factories/validation), convert parse-boundary types to `z.infer`, add the planner drift guard, and make the boundary rules execute in CI.

Already completed in the authoring session (not part of this task): root `node_modules` cache debris deleted, `node_modules/` gitignored, `check-output-redirect.sh` hardened to enforce `--cwd frontend` / `-p backend` (7 test cases green), testing-invocation docs synced (root CLAUDE.md rule 11, backend CLAUDE.md, `.claude/rules/frontend/testing.md`).

Phases, strictly ordered; the full FE suite and `tsc -b` must be green at every phase boundary:

1. **Hygiene (remainder)** — deletions: root screenshots (`detail-page-screenshot.png`, `ego-gift-page.png`, `identity-page.png`, `ssr-identity-full.png`), `network-ts-requests.log`, `frontend/src/App.tsx` + `App.css` (unused create-vite scaffold), `frontend/src/routeTree.gen.ts` + the commented-out router-plugin block in `vite.config.ts`, `frontend/src/routes/` (empty), `.bak` files (`components/notifications/*.bak`, `components/moderator/*` — dir has only `.bak`s), empty dirs (`components/moderator`, `components/themePack`, `components/egoGift`). Stand up the boundary-only flat eslint config as a CI gate.
2. **Pattern consolidation** — see Target §New shared primitives. Adopt at all call sites; delete the per-entity copies.
3. **Type-direction fixes** — convert 7 parse-boundary type files to `z.infer` (static: `KeywordTypes` boundary shapes, `StartBuffTypes` pipeline shapes, `ColorCodeTypes`, `AnnouncementTypes`; API: `CommentTypes`, `NotificationTypes`, `UserSettingsTypes`). Assembled view types (`AbEventListItem`, `ResolvedKeyword`, `ParsedSegment`, …) stay hand-written. Migrate field JSDoc onto schema properties. Where the swap breaks `tsc`, reconcile the schema against what the pipeline/BE actually emits — never `as any`. Add the planner drift guard type-test file.
4. **Structure collapse** — execute the mapping tables below. `git mv` + import-only edits (no code-writer agents on pure moves); one concept/slice per commit; reverse-import grep (dual-consumer check) before each move batch for the flagged files; repoint `vi.mock` paths in moved tests. Extend eslint: deep-import ban on `@/shared/*/**`; sink rule (`components|hooks|lib|schemas|types|shared` may not import `@/pages/*`; `lib` may not import React).
5. **Doc truth-sync (FE)** — `frontend/CLAUDE.md` Quick Reference paths (currently cites `hooks/useEntityListData.ts` etc. and a fictional `lib/validation.ts` — after phase 2 it exists for real), root `CLAUDE.md` import-alias table (add `@/shared`), `.claude/rules/frontend/**` path references.

## Scope

Read for context:
- `frontend/CLAUDE.md`, root `CLAUDE.md`, `.claude/rules/frontend/architecture/page-slice-migration.md` (the proven move playbook — dual-consumer reverse-trace, git mv discipline, vi.mock repointing)
- `frontend/eslint.config.js` (existing boundary rule + router exemption)
- `frontend/src/lib/router.tsx` (composition root), `frontend/src/pages/identity/` (canonical slice shape)
- `docs/tasks/027-fe-planner-migration/` (planner type/schema rationale), `frontend/src/pages/planner/schemas/PlannerSchemas.ts`
- This spec's mapping tables (the file-level record of the design)

## Target

### New shared primitives (phase 2 — create)
| File | Contents |
|---|---|
| `lib/validation.ts` | `validateData(data, schema, context)` — the safeParse-throw pattern, replaces 25+ inline copies (frontend/CLAUDE.md already documents this path) |
| `lib/queryOptions.ts` | `createStaticDataQueryOptions(path, schema, context, {staleTime, keepPrevious})` — collapses the 12 paired data hooks' bodies |
| `lib/queryKeys.ts` | `createEntityListQueryKeys(ns)` / `createEntityDetailQueryKeys(ns)` — collapses 34 hand-rolled factories |
| `components/hooks/useSetFilters.ts` | owns the per-page `Set`-filter states + derived `resetAll` (kills the lockstep `handleResetAll` hazard) |
| `lib/` or `shared/filter` | extracted `areSetsEqual` (byte-identical today in `SinnerFilter`/`KeywordFilter`) |
| mutation + dropdown helpers | thin `useMutation` skeleton factory (invalidate keys + toast config); shared option-building for the 7 search dropdowns |
| `pages/planner/types/__tests__/` (or types-adjacent) | drift-guard type-test file (`Expect<Equal>` leaves; `extends z.input` composites) |

Also: replace the hand-rolled RAF blocks in `IdentityList`/`EGOList` (and detail pages if present) with the existing `useProgressiveReveal`.

### Structure mapping (phase 4)

`shared/<concept>` (vertical: each takes its components + hooks + schemas + types):
| Concept | Aggregates |
|---|---|
| `shared/skill` | `SkillCardLayout`, `SkillDescription`, `SkillImageComposite`, `CoinDisplay`, `SkillTagSchemas`, `useSkillTagI18n` |
| `shared/gameText` | `Formatted*`, `ColoredText`, `LinkifyText`, `StyledName`, `unityRichText`, `keywordFormatter` + `useKeywordFormatter`, `useColorCodes` + `ColorCodeSchemas`, `useSanityConditionData` + `SanityConditionSchemas` (flag: reverse-grep), `useBattleKeywords` + `BattleKeywordsSchemas`, `StartBuffTypes` |
| `shared/gameData` | game vocabulary from `constants.ts` (SINNERS, AFFINITIES, RANK_LEVELS, …), `getAttributeColors`/`getSeasonColor` from `colorUtils` |
| `shared/assets` | `assetPaths` + `assetManifest` (private dep) |
| `shared/filter` | `components/filter/*` (28) + `SearchBar`, `Sorter`, `SearchableMultiSelect`, `useFilterI18nData`, `FilterSchemas`, `FilterTypes`, `filterUtils`, `entitySort`, `useSearchMappings` + `SearchMappingSchemas` |
| `shared/comment` | `components/comment/*` (8) + `useCommentMutations`, `usePlannerCommentsSse`, `CommentSchemas`, `CommentTypes` |
| `shared/notifications` | `components/notifications/*` (4 live) + 5 notification query/mutation hooks, `NotificationSchemas`, `NotificationTypes`, `browserNotification` |
| `shared/auth` | `useAuthQuery`, `useAuthQueryNonBlocking`, `useGoogleLogin`, `useLogoutEverywhere`, `AuthSchemas`, `AuthTypes`, `formatUsername` |
| `shared/sse` | `useSseConnection`, `useSseStore` |
| `shared/noteEditor` | `components/noteEditor` + `tiptap-*` dirs (may stay siblings — vendored) + `NoteEditorSchemas`, `NoteEditorTypes`, `tiptap-utils`, `noteUtils` |
| `shared/moderation` | `BanDialog` (dual-consumer moderator+comment), `BanStatusBanner` |

`components/` (domain-free React UI kit):
| Bucket | Files |
|---|---|
| `ui/` | shadcn (unchanged) + `DiscordIcon`, `GoogleIcon` (dissolve `icons/`) + `KoreanText.tsx` with its `scoreDreamGlyphs` data table (colocation: a component's private data beats the React/non-React split) + `AutoSizeText`, `AutoSizeWrappedText` |
| `layout/` | `DetailPageLayout`, `DetailLeftPanel`, `DetailRightPanel`, `MobileDetailTabs`, `DetailEntitySelector`, `EntityMetaInfo`, `ResponsiveCardGrid`, `VirtualCardGrid`, `ScaledCardWrapper`, `SectionContainer`, `GlobalLayout.tsx`, `Header.tsx` |
| `feedback/` | `ErrorBoundary`, `ErrorState`, `LoadingState`, `DetailPageSkeleton`, `ListPageSkeleton`, `RouteErrorComponent`, `NotFoundPage`, `CommunityPlansErrorFallback` |
| `hooks/` | `use-is-breakpoint`, `useDragToScroll`, `useProgressiveReveal` (root `hooks/` dies) |

Into page slices:
| Destination | Files |
|---|---|
| `pages/identity` | `useIdentityListData`, `useIdentityDetailData` (post-consolidation adoption), `PanicInfoSchemas` (flag: reverse-grep) |
| `pages/ego` | `useEGOListData`, `useEGODetailData`, `egoPassiveSelection` |
| `pages/egoGift` | `useEGOGiftListData`, `useEGOGiftDetailData`, `egoGiftEncoding` |
| `pages/keyword` | `useKeywordListData`, `useKeywordDetailData`, `KeywordSchemas`, `KeywordTypes` |
| `pages/home` | `useAnnouncementData`, `AnnouncementSchemas`, `AnnouncementTypes` |
| `pages/settings` | `useUserSettingsQuery`, `UserSettingsSchemas`, `UserSettingsTypes`, `SyncChoiceDialog`, `useFirstLoginStore` |
| `pages/moderator` | `useHideFromRecommendedMutation`, `useUnhideFromRecommendedMutation`, `useModeratorCommentDelete`, `components/moderation`? — NO: moderation stays shared (dual-consumer) |
| `pages/planner` | `StarlightCostDisplay`, `SectionNoteDialog`, `floorGiftBucketing`, `scoreDreamGlyphs`? — NO: rides with KoreanText in `ui/` |

`lib/` residue (domain-free, ~11 files): `api`, `queryClient`, `router.tsx`, `i18n`, `env`, `toast`, `utils`, `formatDate`, `storage`, `uuid`, `constants` (app-config remainder), + new `validation`/`queryOptions`/`queryKeys`. Delete `hashKey` (zero consumers — final grep for dynamic/string references first).

Dissolved entirely: `components/common`, `components/dialogs`, `components/icons`, root `hooks/`, `schemas/`, `types/`, `stores/`, `routes/`.

### Config / docs (phases 1, 4, 5 — modify)
- `frontend/eslint.config.js` — extend deep ban to `@/shared/*/**`; add sink rules; keep `router.tsx` exemption
- CI workflow — boundary-only flat eslint gate
- `frontend/vite.config.ts` — remove dead router-plugin comment block + `routeTree.gen.ts` reference
- `frontend/CLAUDE.md`, root `CLAUDE.md`, `.claude/rules/frontend/**` — truth-sync

## Invariants

- INV1 Behavior-preserving: full FE suite + typecheck green at every phase boundary — test: `yarn --cwd frontend vitest run`, `yarn --cwd frontend tsc -b`
- INV2 Bundle: Tiptap absent from the entry chunk; no aggregate barrels in shared space; all barrels use named `export {x} from`, never `export *` — test: `yarn --cwd frontend build` + grep entry-chunk assets for tiptap identifiers
- INV3 Boundaries execute: zero deep `@/pages/*/**` or `@/shared/*/**` imports outside `router.tsx`; `shared|components|lib` never import `@/pages`; `lib` never imports React — test: boundary eslint run in CI + grep sweep
- INV4 Pure moves: every relocation shows `R`/`RM` in `git status`; moved-file diffs contain only import-line changes — test: per-phase `git diff` inspection
- INV5 Type conversions honest: `z.infer` swap compiles; any mismatch reconciled by completing the schema against actual emitted JSON (never `as any`) — test: existing schema tests parse real `static/` data + `tsc -b`
- INV6 Planner gate looseness unchanged: `SaveablePlannerSchema` `content` stays `z.record(string, unknown)`; composites guarded one-directionally only — test: drift-guard file compiles; existing planner load/save tests
- INV7 Note byte parity intact through the `noteUtils` move: wrapped−inner === 12 — test: existing `noteUtils.test.ts` regression

## Done When

- [ ] All phase-1 deletions done; `git status` clean of scaffold/debris; boundary lint gate runs in CI
- [ ] 12 paired data hooks reduced to thin per-entity wrappers over the factories; 34 key factories → 2 creators; 25+ validate sites use `lib/validation.ts`; RAF blocks replaced by `useProgressiveReveal`; `areSetsEqual` single-sourced; `useSetFilters` adopted by list pages
- [ ] 7 parse-boundary type files derive via `z.infer`; assembled/internal types untouched; planner drift guard in place and compiling
- [ ] `src/` matches the mapping tables: `shared/` concepts exist with public APIs; `components/` = ui/layout/feedback/hooks only; root `hooks|schemas|types|stores|routes` and `components/common|dialogs|icons` gone; `lib/` ≈ 11 domain-free files
- [ ] `constants.ts`/`assetPaths.ts` split landed with shims removed (179/75 sites flipped)
- [ ] eslint boundary + sink rules active; grep sweeps empty
- [ ] FE docs (`frontend/CLAUDE.md`, root import table, rules files) describe the new tree — no references to deleted paths
- [ ] All existing tests pass; moved tests' `vi.mock` paths repointed

## Test Plan

### Test Runner
- Framework: Vitest (FE), tsc for typecheck
- Run command: `yarn --cwd frontend vitest run > /tmp/fe-test-<session-id>-<suffix>.log 2>&1` (full suite per phase; targeted suites during development); `yarn --cwd frontend tsc -b > /tmp/fe-typecheck-<session-id>-<suffix>.log 2>&1`

### Tests to Write
- [ ] `validateData` success/failure + error-message format (`[context] Validation failed: …`): `lib/__tests__/validation.test.ts`
- [ ] `createStaticDataQueryOptions` queryKey/staleTime/parse-throw behavior: `lib/__tests__/queryOptions.test.ts`
- [ ] `createEntityListQueryKeys`/`DetailQueryKeys` tuple shapes: `lib/__tests__/queryKeys.test.ts`
- [ ] `useSetFilters` set/toggle/resetAll (reset covers every registered key): `components/hooks/__tests__/useSetFilters.test.ts`
- [ ] `areSetsEqual` edge cases (empty, subset, equal): with its new home's `__tests__/`
- [ ] Drift guard: type-test file — compiling IS the test; every planner leaf + composite covered
- [ ] Post-conversion schema tests parse real `static/` JSON fixtures for the 4 static-boundary files
- [ ] INV2/INV3 verification scripts (bundle grep, boundary grep) runnable per phase
- [ ] Every invariant above has its test realized — no invariant ships untested

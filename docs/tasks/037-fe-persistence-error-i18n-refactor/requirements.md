# Task: FE plan-persistence consolidation, error-handling conformance, sync-divergence notice, i18n data fallback

Archives the design debate of 2026-07-07 → 2026-07-08 (single session): a four-dimension FE audit (error handling, page→page dependencies, pages→shared imports, plan persistence), the confirmed dual-query-key bug, and the track-by-track refactor design resolved through dialectic, including the post-036 rebase and two user corrections (per-phase sole-basis rule; S9 → tab-presence warning).

Implementation-grade mechanics live in `mechanics.md` — read it before building any track: it binds the persistence module layout, the codec contract, the SSE event envelope, the lock/channel recipe, and the fallback-chain semantics.

**Baseline:** the post-036 tree (`docs/tasks/036-keyword-migration-validation-relocation/`) — BE canonicalizes content and derives the SET column; the upsert request carries no top-level `selectedKeywords`; FE `migrateKeywords`-on-read and the FE two-tier validator stay as the guest-data defense (036 D10). Do not start this task before 036 is complete.

**N/A:** `docs/spec.md` "Data-Driven Features" sections (Data Model Catalog, Normalization Layer, Rendering Mode Enumeration, Reference Per Mode, Implementation Order) — N/A: this refactor relocates FE infrastructure and consumes no raw game-data files; Track C changes how existing locale JSON is *fetched*, not its shape.

## Decisions

### Track A — persistence

- **D1 (taste): All plan-persistence machinery consolidates into `pages/planner/persistence/`, under a per-phase sole-basis rule — every phase moves code into the consolidation target and deletes the source in the same commit; no responsibility is ever duplicated across a phase boundary.** *Principle: during a consolidation refactor, the intermediate states are part of the design — a phase that leaves two live implementations of one concern recreates the parallel-implementation disease the refactor exists to kill.*
- **D2 (taste): `SaveablePlanner` stays the canonical hub (hub-and-spoke conversion); editor state stays a narrow projection and never absorbs envelope fields (`syncVersion`, `deviceId`, `createdAt`, `schemaVersion`, `status`).** Forced by two facts: server↔disk flows (conflict-discard writes `fetchFromServer` output straight to IndexedDB, `usePlannerSave.ts:775-780`) never touch editor state, and editor state is lossy. *Principle: converge on a canonical data model when some flows bypass the in-memory representation entirely or the in-memory representation is a lossy projection of the durable one; server-owned fields never enter a store that user interactions mutate.*
- **D3 (taste): The content codec normalizes, never rejects — malformed fields repair to defaults and loading continues; rejection lives only at the envelope boundary (`SaveablePlannerSchema`).** *Principle: one bad field must never discard the whole document on load; validation that can reject belongs at the envelope, repair belongs at the content layer.* (Extends the existing `.refine` blast-radius firewall: `content: z.record(z.unknown())` + its pinning test stay.)
- **D4: One codec module, both directions, absorbing all four conversion sites at once** — the three deserializers (`initializeFromPlanner` at `usePlannerEditorStore.tsx:386`, the edit-page inline build at `PlannerMDEditPage.tsx:82-110`, `handleServerReload` at `PlannerMDEditorContent.tsx:301`) **and** `serverResponseToSaveable` (`usePlannerSyncAdapter.ts`), plus the serialize side (`createSaveablePlanner`, `serializeSets`, dirty-string derivation). Keyword migration becomes a codec responsibility; the in-place mutation in `loadPlanner` (`usePlannerStorage.ts:240`) is deleted in the same commit. Guard disagreements between the three deserializers are reconciled explicitly per-field against the characterization net, not merged mechanically. (evidence: the three routines differ in `Array.isArray` guards, default mechanisms, and migration responsibility; the edit-page path crashes on corrupted content that the store path repairs)
- **D5: One `plannerQueryKeys` factory.** (evidence: confirmed latent bug — `useSavedPlannerQuery.ts:8-11` registers `['planner', id]` while `usePlannerSync.ts:32-35` exports a same-named factory whose `detail()` is `['planners','detail',id]`; SSE invalidation at `useAppSse.ts:138-141` therefore prefix-matches no live query)
- **D15 (taste): Dirty/conflict comparison stays local-vs-local — `previousStateRef`/`lastSyncedStateRef` are seeded only from locally-produced `toSaveable()` output, never from server-response content.** *Principle: change detection must compare representations produced by the same serializer; comparing against a peer's canonicalized bytes makes serializer parity load-bearing for correctness.* (Preserves 036 mechanics §5's parity-immunity argument.)
- **D10 (taste): Validation splits by what is checked — schema (Zod) for shape at trust boundaries, imperative code for domain semantics.** The dormant Zod content tier (`MDPlannerContentDraftSchema`/`SaveSchema`, `SavePlannerSchema`, `FloorSelectionSaveSchema`, `validateSaveablePlanner*`) is deleted with its self-referential tests; the imperative two-level validator (`plannerValidation.ts`) stays permanently as the guest-data defense, now mirrored by the BE two-tier (036 D5/D10). *Principle: domain rules need injected reference data, ordered first-error-wins i18n messaging, and freedom from whole-object refine blast radius — parse shape at the boundary, check semantics imperatively past it.*
- **D17: The two byte-counters are co-located and honestly named, not merged** — they measure different things (note JSON-doc bytes vs title text bytes). `MAX_TITLE_BYTES` moves to constants. (evidence: `measureDocBytes` counts serialized Tiptap JSON incl. wrapper; `calculateByteLength` counts TextEncoder bytes of a plain string)
- **D18 (default): canonical key shape is hierarchical** (`['planner','detail',id]`-style) so prefix invalidation works; exact literals bound in `mechanics.md`.

### Track A — error handling

- **D11 (taste): Errors route to their level — query errors are page-level (throw to `RouteErrorComponent`), mutation errors are interaction-level (toast), outages are app-level (global toast); every deviation is fixed by re-routing, not by adding try/catch.** The five outlaw planner hooks (`usePlannerVote/Report/Bookmark/Subscription/Fork`) conform to layer 3 via `useApiMutation`; the three stale rule docs are rewritten to describe the real patterns so the inconsistency cannot regenerate. *Principle: an error-handling architecture is a routing taxonomy; "inconsistent error handling" is almost always a mis-routed error, and stale pattern docs are how fixed inconsistencies come back.*
- **D12: The non-suspense `useQuery` optional-data pattern is sanctioned by documenting it** (silent degradation for genuinely optional data), not converted — it is a legitimate third error level once named. Dead `ErrorState` component is deleted; `FloorThemeGiftSection` switches raw `sonner` → `@/lib/toast`.

### Track B — divergence/deletion notice + tab presence

- **D6 (taste): No SSE-driven refetch of planner detail; instead an early-warning banner with save-time resolution.** SSE `updated` for the open planner (from another device) sets a diverge warning above the title; the existing `syncVersion` optimistic-lock conflict dialog remains the only resolution point. *Principle: early warning, late resolution — never auto-reload a live editing surface from a push event; surface divergence passively and resolve it at the user's own write boundary.* (User decision 2026-07-08, superseding the earlier SSE-refresh idea.)
- **D7: Deleted-elsewhere semantics — the SSE local purge stays; the open plan survives as a local draft only through active editing.** Auto-save is edit-triggered, so purge + resurrect-on-edit natively encodes "deletion wins unless the user demonstrates intent to keep working"; the only added guard is cancelling a pending debounce timer at purge time. After a `serverDeleted` notice, the next manual sync detaches (new server identity, cleared `syncVersion`) instead of blind-upserting against a soft-deleted row. (User decision: keep autosaving as local draft; detach rule adopted from the outline without objection.)
- **D8: BE adds `originDeviceId` to the planner SSE event.** (evidence: `PlannerSseEventSchema` is `{plannerId, type}` `.strict()` — self-originated events echo back and are indistinguishable, so every own-device save would false-trigger the diverge warning; FE time-window heuristics rejected as fragile. User approved the BE change.)
- **D9 (taste): Tab presence via Web Locks as the source of truth, BroadcastChannel as the notification transport.** A held lock proves presence structurally — the browser releases it on tab close/crash, so "lock unavailable" is never stale; heartbeat/localStorage schemes must prove liveness and are racy by construction. *Principle: prefer APIs whose failure mode performs your invalidation for you over liveness protocols you must keep honest yourself.* Composes with (does not replace) the parked compare-before-write guard.

### Track C — i18n data fallback + skeleton primitive

- **D13 (taste): Locale-data fallback happens inside the queryFn (`[locale,'en']` chain resolving `{data, resolvedLocale}`), not in error boundaries.** Missing-locale 404s are consumed by the chain; network/backend-down errors still throw, preserving the error taxonomy. *Principle: language fallback is data substitution, not UI recovery — implement it at the data source so queries succeed with tagged fallback data; error boundaries replace UI and are the wrong tool.* (Also cures the current silent-`undefined` failure of the optional-data i18n hooks.)
- **D14 (taste): Keep the skeleton swap on locale change (over `startTransition` stale-content), but as one reusable primitive — boundary at the text node, sized skeleton fallback, never `fallback=""`.** *Principle: suspense boundaries for text belong at the text node with a size-preserving fallback; zero-width fallbacks cause layout collapse-and-reflow, and per-site bespoke boundaries drift.* (User chose explicit loading feedback over transition continuity.)
- **D16 (evidence): 036 INV7 tripwire** — the parity test pins `frontend/src/shared/gameData/constants.ts` (`KEYWORD_RENAME_MAP`) and `plannerKeywords.json` key equality with the BE; Track C must not move those files, rename the constant, or alter JSON keys. Serving EN keyword *names* via fallback is fine — keys are locale-invariant ids.

### Out of scope (parked, recorded)

- S9 compare-before-write local version stamp — demoted to a follow-up once B2's presence warning ships.
- Stale doc/memory references (project CLAUDE.md eslint→oxlint pointers; the DAG claim; 036 mechanics §5's `usePlannerSyncAdapter` file pointer after A6) — swept at `/wrap`, not here.

## Description

Three independent tracks over the post-036 tree.

**Track A — persistence consolidation (phases A0–A6, strictly ordered):**
1. **A0** — characterization net pinning post-036 behavior: golden-file `SaveablePlanner` JSON contract, save/load round-trip, conflict paths (overwrite/discard/keep-both), deleted-while-editing, pending-debounce-after-conflict, upsert-request shape (no top-level `selectedKeywords`), migration-at-every-read-boundary, publish-400 `KEYWORD_INVALID` surfacing.
2. **A1** — single `plannerQueryKeys` factory; delete the `usePlannerSync.ts` duplicate; repoint every query and invalidation. Pure refactor (no behavior rides on it — SSE refresh is dropped by D6).
3. **A2** — error-handling conformance: five hooks → `useApiMutation` (`ConflictError` branches via `onError` override); delete the stale TODO (`usePlannerVote.ts:106`) and the false "shows toast" comment (`PlannerCardContextMenu.tsx`); `sonner`→`@/lib/toast`; delete `ErrorState`; rewrite `.claude/rules/frontend/components/{loading-error,mutations,forms}.md` to the real patterns; document the optional-data `useQuery` level.
4. **A3** — the codec (`plannerCodec.ts`): `toEditorState`/`toSaveable`/`fromServerResponse` + composed conveniences; per D4 conditions; dirty-string derived from `toSaveable` output (collapses the triple serialize).
5. **A4** — validation consolidation per D10/D17.
6. **A5** — one IndexedDB layer: `lib/storage.ts` gains key iteration; `usePlannerStorage`'s private `openDB` dies; `usePlannerSaveAdapter` deleted here; `clearCorruptedPlanner` wired into the load-failure path (corrupted draft → offer deletion); stale JSDoc (2s vs 1000ms) fixed.
7. **A6** — god-hook split: `useAutoSave` / `usePlannerPersistence` / `useConflictResolution`; `plannerServerSync.ts` absorbs the by-then-skeletal `usePlannerSyncAdapter` (deleted); single publish owner (`usePublishState`) wrapping both write paths; `KEYWORD_INVALID`→`handleSaveError` mapping carried through.

**Track B — divergence/deletion notice + tab presence (after the BE event change):**
- **B-prereq (BE):** `originDeviceId` on the planner SSE event; FE `PlannerSseEventSchema` updated in the same deploy window (schema is `.strict()`).
- **B1:** `useAppSse.handlePlannerUpdate` sets `serverDiverged`/`serverDeleted` on the open editor's store when the event matches the open planner and `originDeviceId` differs; warning banner above the title using a new `warning` color token (no hardcoded yellow); deleted-flow per D7.
- **B2:** tab-presence warning per D9 (`openElsewhere` state, same banner slot).

**Track C — i18n (independent):**
- **C1:** `fetchLocaleJson` fallback chain wired through the game-text/i18n query-options factories (~10 hooks), D13 semantics, D16 tripwire respected.
- **C2:** `SuspendedText` + sized `TextSkeleton` primitive in `shared/gameText`; existing bespoke skeletons (`StyledNameSkeleton`) become presets; documented pairing rule with the `*Deferred` escape-hatch hooks.

Sequencing: A0 first; A1/A2 parallelizable after it; A3→A4→A5→A6 strictly ordered; B and C independent of A and of each other.

## Scope

- `docs/tasks/036-keyword-migration-validation-relocation/{requirements,mechanics}.md` — baseline contracts
- `frontend/src/pages/planner/hooks/` — `usePlannerSave.ts`, `usePlannerStorage.ts`, `usePlannerSync.ts`, `useSavedPlannerQuery.ts`, `usePublishedPlannerQuery.ts`, `usePlannerSyncAdapter.ts`, `usePlannerSaveAdapter.ts`, `useAppSse.ts`, `usePlannerVote.ts`, `usePlannerReport.ts`, `usePlannerBookmark.ts`, `usePlannerSubscription.ts`, `usePlannerFork.ts`, `usePlannerPublish.ts`, `useMDUserPlannersData.ts`
- `frontend/src/pages/planner/stores/usePlannerEditorStore.tsx`, `PlannerMDEditPage.tsx`, `components/planner/PlannerMDEditorContent.tsx`, `components/plannerViewer/{GuideModeViewer,TrackerModeViewer}.tsx`, `components/plannerList/PlannerCardContextMenu.tsx`
- `frontend/src/pages/planner/schemas/PlannerSchemas.ts`, `lib/plannerValidation.ts`, `lib/plannerApi.ts`, `types/PlannerTypes.ts`
- `frontend/src/lib/{storage.ts,queryClient.ts,api.ts,toast.ts,constants.ts,utils.ts}`, `frontend/src/components/hooks/useApiMutation.ts`, `components/feedback/{ErrorState,RouteErrorComponent}.tsx`
- `frontend/src/shared/gameText/` (query factories, `StyledName.tsx`), `frontend/src/shared/gameData/{constants.ts,keywordNormalize.ts}`, `frontend/src/shared/noteEditor` (byte counting)
- `.claude/rules/frontend/components/{loading-error,mutations,forms}.md`
- BE: the planner-update SSE producer (locate via `grep -r "planner-update" backend/src/main`) and its event DTO

## Target

**CREATE:**
- `frontend/src/pages/planner/persistence/` — `plannerQueryKeys.ts` (A1), `plannerCodec.ts` (A3), `plannerLocalStore.ts` (A5), `plannerServerSync.ts` (A6), `useAutoSave.ts`, `usePlannerPersistence.ts`, `useConflictResolution.ts`, `usePublishState.ts` (A6) — layout bound in `mechanics.md` §2
- Characterization/round-trip test suites under `frontend/src/pages/planner/persistence/__tests__/` (+ golden fixtures)
- `warning` color token (theme/constants — B1)
- `shared/gameText` — `SuspendedText`, `TextSkeleton`, `fetchLocaleJson` (C)
- BE: `originDeviceId` field on the planner SSE event + test

**MODIFY:** every Scope file that currently holds a responsibility being moved; rule docs rewritten (A2).

**DELETE (at the named phase):** duplicate `plannerQueryKeys` in `usePlannerSync.ts` (A1); `ErrorState.tsx`, stale TODO/comment (A2); the three deserializers + `serverResponseToSaveable`'s body + `loadPlanner`'s in-place migration (A3); dormant Zod content tier + self-referential tests (A4); `usePlannerStorage.ts`'s private `openDB` + `usePlannerSaveAdapter.ts` (A5); `usePlannerSyncAdapter.ts` (A6).

## Invariants

- INV1: Exactly one `plannerQueryKeys` definition exists; every planner query and every invalidation uses it — test: grep-based module test + integration test that an invalidation with the factory's `detail(id)` refetches the registered detail query.
- INV2: Every planner mutation failure is user-visible — vote/report/bookmark/subscription/fork each toast on error — test: per-hook vitest asserting toast on rejected mutation.
- INV3: Codec round-trip — `toSaveable(toEditorState(x))` preserves content for all golden fixtures — test: round-trip property test over the fixture set.
- INV4: Codec never throws on malformed content; corrupted fixtures produce repaired state (defaults), not exceptions — test: corrupted-fixture suite (non-array collections, missing fields, null floors).
- INV5: A renamed keyword id entering by any read boundary (IndexedDB load, server fetch, editor init, viewers) exits migrated — test: per-boundary fixture through the codec (guards 036 D10).
- INV6: The upsert request contains no top-level `selectedKeywords` — test: request-shape assertion in the sync tests (036 regression shield; BE silently ignores the field, so only this test catches a resurrection).
- INV7: Dirty/conflict comparison is local-vs-local — a server response whose content bytes differ only in serialization does not mark the editor dirty — test: seed `lastSynced` from local `toSaveable`, feed re-ordered-key server content, assert not-dirty.
- INV8: `SaveablePlannerSchema.content` remains `z.record(z.unknown())` — test: existing pinning test stays green, untouched.
- INV9: The stored `SaveablePlanner` JSON shape is unchanged — test: golden-file byte/structure comparison (compatibility contract with existing users' IndexedDB).
- INV10: One IndexedDB open path in `frontend/src` — test: grep test for `indexedDB.open`/`openDB` occurrences = 1 module.
- INV11: The diverge/deleted banner triggers only for events with `originDeviceId !== own` — test: handler unit test, own-device event → no state change.
- INV12: On SSE `deleted` for the open planner: pending debounce is cancelled, row purged; a subsequent user edit recreates it as a draft; a subsequent manual sync detaches (no upsert with the old `syncVersion`) — test: fake-timer sequence test.
- INV13: A second tab opening the same planner's editor detects the held lock and both tabs show `openElsewhere` — test: mocked `navigator.locks`/`BroadcastChannel` unit tests (crash-release is browser-guaranteed; documented, not tested).
- INV14: A missing locale file yields EN data tagged `resolvedLocale:'en'` without throwing; a network/5xx error still throws — test: fetch-mock suite on `fetchLocaleJson` + one wired query factory.
- INV15: `KEYWORD_RENAME_MAP` location and `plannerKeywords.json` keys are untouched — test: 036's parity test stays green, unmodified.
- INV16: Publish-400 `KEYWORD_INVALID` surfaces as a user-visible error through the post-split save path — test: mocked 400 through `usePlannerPersistence` → error state/toast.
- INV17: Per-phase sole basis — at every phase boundary, grep for the moved symbols' old definitions returns zero and the suite is green — test: per-phase checklist executed in `/build`.

## Done When

- [ ] `pages/planner/persistence/` holds all persistence machinery; `usePlannerSaveAdapter.ts`, `usePlannerSyncAdapter.ts`, and the three deserializers no longer exist
- [ ] SSE invalidation and all planner queries share one key factory (INV1 integration test green)
- [ ] Vote/report/bookmark/subscription/fork failures toast; the stale TODO and false comment are gone
- [ ] The dormant Zod content tier is deleted; `plannerValidation.ts` remains the only content validator; envelope pinning test untouched and green
- [ ] Corrupted-draft load offers deletion instead of a permanent `PlannerNotFound`
- [ ] Editing on one device while another device updates the plan shows the yellow diverge banner (token-based); deletion elsewhere shows the deleted notice; own-device saves trigger neither
- [ ] Opening the same plan's editor in two tabs warns in both
- [ ] Switching to a locale with missing game-text data renders EN text (no crash, no blank), with text-node-sized skeletons during the swap
- [ ] The three rule docs describe the real error patterns (`@/lib/toast`, throw-to-boundary, `useApiMutation`, optional-data level)
- [ ] All existing FE tests pass; `tsc -b` green; 036's parity and byte-parity tests green; BE suite green after the SSE event change

## Test Plan

### Test Runner
- Frontend: Vitest — `yarn --cwd frontend vitest run src/pages/planner src/shared/gameText` + `yarn --cwd frontend tsc -b`; output redirected to `/tmp/<prefix>-<session-id>-<suffix>.log` per project convention. Test files in `__tests__/` subdirectories.
- Backend: JUnit 5 — `./gradlew -p backend test --tests "org.danteplanner.backend.planner.*"` (SSE event DTO + producer).

### Tests to Write
- [ ] A0 characterization suite: golden-file `SaveablePlanner`, save/load round-trip, three conflict resolutions, deleted-while-editing, pending-debounce-after-conflict, request shape (INV6), migration-per-boundary (INV5), publish-400 surfacing (INV16) — `pages/planner/persistence/__tests__/` (written against the *pre-refactor* code, kept green throughout)
- [ ] `plannerQueryKeys` integration: invalidation refetches registered query (INV1)
- [ ] Five mutation hooks: toast-on-error (INV2)
- [ ] Codec: round-trip property (INV3), corrupted-fixture repair (INV4), dirty-string local-vs-local (INV7)
- [ ] Storage: single-open-path grep test (INV10), corrupted-draft deletion offer, key iteration
- [ ] SSE banner: own-device suppression (INV11), deleted sequence with fake timers (INV12)
- [ ] Tab presence: lock-unavailable branch + channel notification, mocked APIs (INV13)
- [ ] `fetchLocaleJson`: fallback chain, tag, error taxonomy (INV14)
- [ ] `SuspendedText`: renders sized fallback while suspended, text on resolve
- [ ] BE: SSE event carries `originDeviceId`; existing event consumers unaffected
- [ ] Every invariant above has its test realized here — no invariant ships untested

# Mechanics — FE persistence/error/i18n refactor

Companion to `requirements.md`. Transcribed from the 2026-07-07 → 2026-07-08 session. Contracts below (module layout, codec behavior, event envelope, lock recipe, fallback semantics, phase sequencing) are **binding**; worked traces (the 15-hop analysis, the deserializer diff, the error-layer audit) are **reference** — see the session summary in the archived debate, not re-transcribed here.

## 1. Persistence module layout (binding — end state after A6)

| File (`pages/planner/persistence/`) | Owns | Absorbs (source deleted at phase) |
|---|---|---|
| `plannerQueryKeys.ts` | the only key factory | both existing factories (A1) |
| `plannerCodec.ts` | all `SaveablePlanner` conversions, both directions | 3 deserializers, `deserializeSets`/`serializeSets`, `createSaveablePlanner`, `serverResponseToSaveable`, dirty-string derivation (A3) |
| `plannerLocalStore.ts` | sole IndexedDB accessor (built on `lib/storage`, which gains key iteration) | `usePlannerStorage.ts` incl. its private `openDB`; `usePlannerSaveAdapter.ts` dies here (A5) |
| `plannerServerSync.ts` | server API calls + `syncVersion` optimistic locking | skeletal `usePlannerSyncAdapter.ts` (A6) |
| `useAutoSave.ts` | store subscribe + 1000ms debounce + dirty check + local write | split from `usePlannerSave` (A6) |
| `usePlannerPersistence.ts` | manual save/publish orchestration, sync ordering, envelope ref bookkeeping, `KEYWORD_INVALID`→`handleSaveError` | split from `usePlannerSave` (A6) |
| `useConflictResolution.ts` | 409 dialog + overwrite/discard/keep-both saga | split from `usePlannerSave` (A6) |
| `usePublishState.ts` | single publish owner wrapping BOTH write paths (editor save, card toggle) | publish plumbing from `usePlannerSave` + `usePlannerPublish` call sites (A6) |

**Per-phase sole-basis rule (binding, D1):** each phase's commit moves the responsibility in AND deletes the source. Checkpoint = suite green + grep for the old symbol returns zero. No phase may end with two live implementations of one concern.

## 2. Canonical query keys (binding)

```ts
plannerQueryKeys = {
  all:       ['planner'] as const,
  list:      () => ['planner', 'list'] as const,
  detail:    (id) => ['planner', 'detail', id] as const,
  published: (id) => ['planner', 'published', id] as const,
}
```

Hierarchical so prefix invalidation works (`{queryKey: plannerQueryKeys.all}` sweeps everything). Existing keys being replaced: `['planner', id]` (useSavedPlannerQuery), `['publishedPlanner', id]`, `['planners','list']`, `['planners','detail',id]` (dead). Gesellschaft/user-planner key families keep their own factories — out of scope unless they collide.

## 3. Codec contract (binding)

```ts
// pages/planner/persistence/plannerCodec.ts
toEditorState(p: SaveablePlanner): Partial<PlannerEditorState>   // the ONE projection
toSaveable(state: PlannerEditorState, env: PlannerEnvelope): SaveablePlanner
fromServerResponse(res: ServerPlannerResponse): SaveablePlanner  // envelope mapping only
loadEditorState(res: ServerPlannerResponse) = toEditorState(fromServerResponse(res)) // convenience
dirtyString(state: PlannerEditorState): string                   // derived FROM toSaveable output
```

- **Guard union (per-field reconciliation, from the deserializer diff):** `Array.isArray` guard on every collection; per-floor `themePackId ?? null`, `difficulty ?? NORMAL`, guarded `giftIds`; explicit `createDefault*()` for `equipment`/`floorSelections`/`skillEAState`; `deckFilterState` always reset to default; sectionNotes = defaults-merge + `note?.content ?? ''`. Where the store action and the edit-page inline build disagreed, the store action's behavior wins (it is the guarded one); the edit page's *implicit-defaults-via-initial-state* mechanism is retired.
- **Migration:** `migrateKeywords` runs unconditionally and idempotently inside `toEditorState` AND inside `deriveSet`-equivalent write paths. The in-place mutation in `loadPlanner` is deleted in the same commit (double-migration would mask a broken codec path).
- **Normalize, never reject (D3):** the codec has no throw path for content. Envelope problems are `SaveablePlannerSchema`'s job, upstream.
- **Dirty semantics (D15):** `previousStateRef`/`lastSyncedStateRef` seeded ONLY from local `dirtyString`/`toSaveable`. Server-response content never enters a comparison.
- **Editor state stays narrow (D2):** `PlannerEnvelope` (`id`, `deviceId`, `createdAt`, `syncVersion`, `schemaVersion`, `status`, `published`) lives with the persistence hooks, not in the Zustand store. (`published` additionally surfaces via `usePublishState` — the one exception already in store cold state; it becomes read-through, single-writer.)

## 4. Error-handling conformance (binding table)

| Hook | Change |
|---|---|
| `usePlannerVote` | → `useApiMutation`, `errorToastKey`; `ConflictError` (already-voted) branch via `onError` override; delete the stale TODO |
| `usePlannerReport` / `usePlannerBookmark` / `usePlannerSubscription` | → `useApiMutation` with `errorLogPrefix` + `errorToastKey` |
| `usePlannerFork` | → `useApiMutation`; callers keep `onSuccess` navigation; failure toasts |
| `PlannerCardContextMenu` | delete the false "Error handled by hook (shows toast)" comment |
| `FloorThemeGiftSection` | `sonner` → `@/lib/toast` |

Rule-doc rewrite (A2): `loading-error.md` documents throw-to-boundary + `RouteErrorComponent` (delete the `ErrorState` prescription); `mutations.md`/`forms.md` show `@/lib/toast` + `useApiMutation`; a new short section names the **optional-data level** (non-suspense `useQuery`, silent degradation, when it is allowed).

## 5. SSE event + banner (binding)

- **Event envelope becomes** `{plannerId, type, originDeviceId}` — `PlannerSseEventSchema` is `.strict()`, so BE emit and FE schema must land in the same deploy window (BE may add the field first only if FE schema tolerates it — it does not; coordinate as one release).
- **Banner state:** `serverDiverged: boolean`, `serverDeleted: boolean`, `openElsewhere: boolean` in editor-store cold state; rendered in one slot above the title; text via i18n keys; color via the new `warning` token (added to the theme/constants — never a hardcoded hex/Tailwind yellow).
- **Trigger:** `useAppSse.handlePlannerUpdate` — event matches the open editor's plannerId AND `originDeviceId !== ownDeviceId` (own id from the existing device-id accessor).
- **Deleted flow (binding sequence):** cancel pending auto-save debounce → purge local row (existing) → set `serverDeleted`. Subsequent user edit → auto-save recreates as `draft` (intended). Subsequent manual sync → detach: new server identity, `syncVersion` cleared; never upsert the soft-deleted id with the stale version.
- **Resolution stays save-time:** the banner never triggers `onServerReload`; the 409 conflict dialog is unchanged.

## 6. Tab presence (binding recipe)

- Lock name: `planner-edit-<plannerId>`. On editor mount: `navigator.locks.request(name, {mode:'exclusive', ifAvailable:true}, holdUntilUnmount)`.
  - Acquired → primary; hold the lock for the editor's lifetime (resolve the held promise on unmount).
  - Not acquired → secondary; set `openElsewhere`; post `{type:'editor-opened', plannerId}` on BroadcastChannel `planner-presence`.
- Primary listens on the channel; on `editor-opened` for its plannerId → sets its own `openElsewhere`.
- Crash safety is the browser's lock release — no heartbeats, no persisted presence state anywhere.
- Feature-detect both APIs; absent (old browser) → silently skip (warning is progressive enhancement).

## 7. i18n fallback + skeleton primitive (binding)

- `fetchLocaleJson(pathFor: (locale) => string, locale: string)`: try `locale`, on 404/parse-failure try `'en'`; return `{data, resolvedLocale}`. Non-404 HTTP errors and network failures rethrow (taxonomy preserved: `ServiceUpdatingError`/`BackendUnavailableError` etc. pass through).
- queryKey keeps the **requested** locale; consumers read `data`; UI may inspect `resolvedLocale` for a degraded-language hint (optional, not required by any invariant).
- Applied to the game-text/i18n query-options factories (keyword i18n, search mappings, identity/ego/egoGift list i18n, planner keywords i18n — the ~10 currently silent-`undefined` hooks). The `*Deferred` non-suspense variants keep their semantics, now over the fallback-capable factories.
- `SuspendedText`: wraps exactly one text node; prop-driven `TextSkeleton` fallback with explicit width/char-length sizing (no `fallback=""` anywhere after C2); `StyledNameSkeleton` becomes a preset of `TextSkeleton`. Pairing rule documented beside the component: suspending reads use `SuspendedText`; render-critical filter paths use the `*Deferred` hook and never suspend.
- **036 INV7 tripwire:** `shared/gameData/constants.ts` (`KEYWORD_RENAME_MAP`) and `plannerKeywords.json` keys must not move or change. (D16)

## 8. Sequencing (binding)

| Order | Phase | Gate |
|---|---|---|
| 0 | 036 complete (BE deployed, FE field-drop shipped) | hard prerequisite |
| 1 | A0 | net green against pre-refactor code |
| 2 | A1, A2 (parallelizable) | INV1/INV2 |
| 3 | A3 → A4 → A5 → A6 (strict) | per-phase sole-basis checkpoint (INV17) |
| any after 0 | B-prereq (BE) → B1 → B2 | one deploy window for the schema change |
| any after 0 | C1 → C2 | INV14/INV15 |

## 9. Parked (reference)

- S9 compare-before-write: local `lastModifiedAt`/version stamp checked before IndexedDB write, mirroring the server's optimistic-lock shape — follow-up after B2.
- Doc/memory staleness sweep (CLAUDE.md oxlint pointers, DAG claim, 036 §5 file pointer) — `/wrap`.

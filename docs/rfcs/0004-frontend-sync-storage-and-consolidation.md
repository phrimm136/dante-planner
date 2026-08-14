---
status: Accepted
tracking: none
---

# 0004 Frontend sync identity, storage, and consolidation

## Scope

The frontend half of four already-argued designs — sync identity (ADR 071, paired with RFC 0003
stream 1), IndexedDB read fallibility and key migration (ADR 074), SSE reduced to notification
transport (ADR 073), and the error/effect conventions the frontend owes the backend conventions in
RFC 0002 (ADR 072). Eight streams. Streams 1–3 change how a save is identified, stored, and
reconciled and must land together with their backend counterparts; streams 4–8 are independently
landable and carry the consolidation work each stream's seam exposes. Every stream below states its
target design, the snippets that bind it to real signatures, the ordered change list, and its test
plan. Stream 8 is a table-only appendix of mechanical and structural work with no design content.

Decision references are `NNN @tag` addresses into `docs/adr/`; those bullets are authoritative and
this document is the implementation contract, not the record.

---

## Stream 1 — Sync identity

Refs: 071 @sync @identity, 071 @sync @digest, 071 @sync @adoption.

### Target design

The client identifies a save by `syncVersion` alone. Content digests exist only server-side: the
write path computes them and compares them at the conflict check (RFC 0003), so a stale-version
write over byte-identical content is not a conflict — and no digest field crosses the wire in
either direction. The client neither computes, stores, nor compares digests.

Version state inside `usePlannerSave` splits into a pure reader and a single writer replacing
`adoptSyncVersion` (`hooks/usePlannerSave.ts:376-381`): the writer takes its version from the
acknowledgement of the request the caller just awaited — HTTP pairing is what ties an ack to the
payload that produced it, and the server's version check is what rejects stale concurrent writes.

```ts
// pages/planner/types/PlannerTypes.ts
/** What the server confirms about a write: which version it assigned. */
export interface ServerAck {
  syncVersion: number
}
```

```ts
/** The version the next write presents. Forward-only, and it writes nothing. */
const presentedVersion = (): number =>
  Math.max(syncVersionRef.current, initialSyncVersion ?? INITIAL_SYNC_VERSION)

/** Adopt the version the awaited response assigned. The only writer of syncVersionRef. */
const adoptAck = (incoming: ServerAck): void => {
  syncVersionRef.current = incoming.syncVersion
}
```

`adoptAck` may move the version backwards, and that is correct at the two sites that adopt server
content in the same step (`:709`, `:721`): the local copy becomes the server's copy, so its version
is the server's version, and the write must be confirmed before the version is adopted. The
pre-force-push read (`:762-765`) is forward-only — it adopts no content, so it may never lower the
presented version. Only `presentedVersion` is forward-only by construction.

`syncPlan.ts` grows a per-row decision extracted out of the loop:

```ts
// pages/planner/lib/syncPlan.ts
export type PlannerVerdict = 'pull' | 'conflict' | 'skip'

/** What one server row means against its local counterpart. */
export function categorizePlanner(
  local: PlannerSummary | undefined,
  server: PlannerSummary,
): PlannerVerdict {
  if (!local) return 'pull'
  if (versionOf(server) <= versionOf(local)) return 'skip'
  return local.status === 'draft' ? 'conflict' : 'pull'
}
```

**Coexistence window.** The backend currently emits `contentDigest` on planner responses and
summaries; the digest is being withdrawn from the wire entirely (server-side comparison only, RFC
0003). Until the backend removes the field, the two `.strict()` schemas
(`ServerPlannerSummarySchema` `schemas/PlannerSchemas.ts:611-628`, `ServerPlannerResponseSchema`
`:572-605`) carry `contentDigest: z.string().optional()` as a tolerated, never-read field. Removing
that line when the backend stops emitting the key is part of RFC 0003's wire cleanup, not a
follow-up.

`runSync` fetches its pull residue in one call instead of a per-id loop:

```ts
// pages/planner/lib/plannerApi.ts
/**
 * Fetch several planners chunked to the server's id cap, yielding each parsed
 * chunk as it lands so a failing chunk cannot discard rows already fetched.
 * Responses are bare arrays, not positionally aligned with the request: ids
 * naming nothing, a deleted planner, or another user's planner are simply absent.
 */
async *batchChunks(ids: string[]): AsyncGenerator<ServerPlannerResponse[]> {
  for (let i = 0; i < ids.length; i += BATCH_PULL_MAX_IDS) {
    const data = await ApiClient.post(`${PLANNERS_BASE}/batch`, {
      ids: ids.slice(i, i + BATCH_PULL_MAX_IDS),
    })
    yield ServerPlannerBatchResponseSchema.parse(data)
  }
},
```

`BATCH_PULL_MAX_IDS` is `PlannerConstants.BATCH_PULL_MAX_IDS` from RFC 0003, mirrored into
`lib/constants/planner.ts`; the request is rejected outright above it, so chunking is required, not
an optimization. An empty `ids` array is `@NotEmpty` on the server, so a pull residue of zero rows
must not issue a request at all.

### Ordered change list

1. `types/PlannerTypes.ts` — add `ServerAck` (version only).
2. `schemas/PlannerSchemas.ts` — the tolerated optional `contentDigest` on
   `ServerPlannerResponseSchema` (`:572`) and `ServerPlannerSummarySchema` (`:611`) per the
   coexistence window; new
   `ServerPlannerBatchResponseSchema = z.array(ServerPlannerResponseSchema)` — the endpoint answers
   a bare array, so an object wrapper would fail every parse.
3. `hooks/usePlannerSyncAdapter.ts` — return the `ServerAck` alongside the `SaveablePlanner` from
   `syncToServer` (`:111-138`).
4. `hooks/usePlannerSave.ts` — replace `adoptSyncVersion` (`:376-381`) with `presentedVersion` and
   `adoptAck`; call `presentedVersion()` at the two save sites (`:478`, `:573`); route the three
   raw `syncVersionRef.current = …` writes (`:495`, `:709`, `:721`) through `adoptAck`, adopting
   only after the local write is confirmed at the server-copy sites; the pre-force-push read
   (`:762-765`) becomes a forward-only take.
5. `hooks/usePlannerSave.ts` — introduce `INITIAL_SYNC_VERSION = 1` in `lib/constants/planner.ts`
   and use it at the seed (`:358`, currently `?? 1`), at the return (`:884`, currently `?? 0`), at
   `usePlannerFork.ts:123`, and at `PlannerSchemas.ts:243`; the wire type is
   `z.number().int().positive()`, so `0` was never a reachable server version.
6. `lib/syncPlan.ts` — extract `categorizePlanner` from the `categorizeSync` loop (`:52-66`);
   `categorizeSync` keeps its three-way partition and calls the extracted function per row.
7. `lib/plannerApi.ts` — add `batch`, yielding per chunk so a failing chunk cannot discard rows
   already fetched.
8. `lib/constants/planner.ts` — mirror `BATCH_PULL_MAX_IDS` from RFC 0003 (value 50, per
   `PlannerConstants.java`).
9. `hooks/useMDUserPlannersData.ts` — replace the per-id `fetchFromServer` loop in `runSync`
   (`:244-260`) with the chunked batch pull, keeping the per-planner save and the `syncedCount`
   accounting; rows are written as each chunk parses. An empty residue issues no request. The
   sites persisting server state outside `usePlannerSave` (`:440`, `usePlannerHeaderActions.ts:106`,
   `PersonalPlannerHeader.tsx:159`) take their version from the returned ack, not from
   `synced.planner.metadata`.

### Test plan

- `lib/__tests__/syncPlan.test.ts` — `categorizePlanner` table: higher server version over a draft
  → `conflict`, over a saved row → `pull`; equal or lower server version → `skip`; no local row →
  `pull`.
- `hooks/__tests__/usePlannerSave.test.ts:527-577` — the two version-adoption pins are updated to
  the `ServerAck` return shape; a save's ack moves the presented version forward; the pre-force-push
  take never lowers it.
- `hooks/__tests__/useMDUserPlannersData.test.tsx` — a pull of three rows issues exactly one batch
  request; an empty residue issues none; rows the response omits are left local-untouched; a chunk
  failure keeps the rows chunks before it already delivered.
- `lib/__tests__/plannerApi.test.ts` — chunking at the cap boundary: at-cap → one request, one-over
  → two; an empty ids array issues no request.

---

## Stream 2 — Storage reads and key migration

Refs: 074 @storage @fallibility, 074 @storage @keys, 074 @storage @migration.

### Target design

`storage.getItem` reports why a read failed instead of answering `null` for both "absent" and
"broken". The shared connection promise is cleared on failure so a transient open error does not
poison every later read, and the connection yields when another tab requests an upgrade.

```ts
// lib/storage.ts
/** Why a read could not be performed. Absence is not a failure — it is `ok(null)`. */
// lib/result.ts gains the shared shape for every discriminated error union in the app.
// Unions stay per-boundary (StorageReadError, SaveError, AppError) so each switch remains
// exhaustively narrow; Tagged only dedupes the member shape, it never merges the unions.
export type Tagged<K extends string, P = object> = { kind: K } & P

export type StorageReadError = Tagged<'notInBrowser'> | Tagged<'ioError', { cause: unknown }>

async getItem(key: string): Promise<Result<string | null, StorageReadError>>
```

Keys drop the type and device segments: `planner:{id}` replaces
`planner:md:{deviceId}:{plannerId}`. The device id remains a one-part singleton key (`deviceId`),
which parses to `null` under both the old and the new parser and so needs no special case. The
migration runs inside the `versionchange` transaction at `DB_VERSION` 2, copy-verify-delete per
key, newest `lastModifiedAt` winning a collision.

```ts
// lib/storage.ts
const DB_VERSION = 2

dbPromise = new Promise((resolve, reject) => {
  const request = indexedDB.open(DB_NAME, DB_VERSION)

  request.onerror = () => {
    dbPromise = null
    reject(request.error)
  }
  request.onblocked = () => {
    dbPromise = null
    reject(new Error('IndexedDB upgrade blocked by another tab'))
  }
  request.onsuccess = () => {
    request.result.onversionchange = () => {
      request.result.close()
      dbPromise = null
    }
    resolve(request.result)
  }
  request.onupgradeneeded = (event) => {
    const { oldVersion } = event as IDBVersionChangeEvent
    const db = (event.target as IDBOpenDBRequest).result
    if (oldVersion < 1) db.createObjectStore(STORAGE_STORE_NAME)
    if (oldVersion < 2) {
      migrateToFlatKeys((event.target as IDBOpenDBRequest).transaction!)
    }
  }
})
```

`getOrCreateDeviceId` (`hooks/usePlannerStorage.ts:154-177`) must not mint a new id on a failed
read: a failed read followed by a mint orphans every row written under the previous device id.
Under the new `Result`, `err` returns without writing and the caller reports the failure; only
`ok(null)` mints.

The key builder's device parameter disappears, so the hand-rolled seventh site in the router
(`lib/router.tsx:48`) has nowhere to hide. It is extracted **before** the flip, so the flip is a
single coherent change across six sites rather than seven with one out of reach.

```ts
// pages/planner/hooks/usePlannerStorage.ts
const storageKeys = {
  /** Planner row key: planner:{plannerId} */
  planner: (plannerId: string) => `${PLANNER_STORAGE_KEYS.PLANNER}:${plannerId}`,
  deviceId: () => PLANNER_STORAGE_KEYS.DEVICE_ID,
}

function parseStorageKey(key: string): { prefix: string; plannerId: string } | null {
  const parts = key.split(':')
  if (parts.length !== 2) return null
  return { prefix: parts[0], plannerId: parts[1] }
}
```

### Ordered change list

1. **Preceding step.** Extract `loadPlannerTitle` (`lib/router.tsx:43-57`) into the planner slice
   and export it from `pages/planner/index.ts`; it builds its key with the real builder and returns
   the placeholder only for `ok(null)`, reporting an `ioError` rather than swallowing it (`:54`).
   `router.tsx:231` and `:243` call it through the router's sanctioned deep-path exemption, not the
   barrel: a static barrel edge from the eagerly-loaded router drags slice components into the
   entry chunk — verified by chunk-content markers, and invisible to a size-only comparison, since
   re-chunking can shrink the entry while leaking.
2. `lib/storage.ts` — `StorageReadError`; `getItem` returns `Result<string | null,
   StorageReadError>`; `dbPromise = null` on `onerror`; `onblocked` and `onversionchange` handlers.
   The write side adopts the same shape: `setItem`/`removeItem` return `Result<void, …>` — a
   storage layer whose reads are fallible but whose writes swallow converts a stuck upgrade into
   silent data loss, since every caller is told the save succeeded. Handler-owned promise state is
   identity-guarded: a stale open request's late events must not clobber a newer `dbPromise` or
   leak its connection.
3. `lib/storage.ts` — `DB_VERSION` 2; `onupgradeneeded` switches on `oldVersion` but keeps the
   store-existence self-heal; `migrateToFlatKeys` copies each `planner:md:*:*` key to
   `planner:{id}`, verifies the written value, deletes the source, and on collision keeps the row
   whose parsed `metadata.lastModifiedAt` is newest. A source row is deleted only under a strictly
   newer winner or a byte-identical one: an exact timestamp tie (or two unreadable timestamps)
   with differing content retains the loser's source row and logs the choice — the migration runs
   once, so an arbitrary destructive pick is unrecoverable. Migration request and transaction
   errors are handled and logged with their cause; a verify mismatch is loud, never a silent
   `return`. The `deviceId` key is skipped by the same parse that skips it at read time.
4. Flip, one change: `hooks/usePlannerStorage.ts` builder `:22-28`; build sites `:210`, `:238`,
   `:432`; parse sites `:298`, `:380`; `parseStorageKey` `:35-46`.
5. `hooks/usePlannerStorage.ts:154-177` — `getOrCreateDeviceId` refuses to mint on an `err` read.
6. All other `storage.getItem` callers adopt the `Result`.

### Test plan

- `lib/__tests__/storage.test.ts` — an `onerror` open leaves `dbPromise` null, so the next call
  reopens; a rejected open surfaces `{kind:'ioError'}`, not `null`; absence is `ok(null)`.
- `lib/__tests__/storageMigration.test.ts` — a v1 database holding `planner:md:devA:p1` and
  `planner:md:devB:p1` upgrades to a single `planner:p1` carrying the newer `lastModifiedAt`; the
  `deviceId` key survives verbatim; source keys are gone; a second open at v2 is a no-op.
- `hooks/__tests__/usePlannerStorage.test.ts` — a failed device-id read returns the failure and
  writes nothing; `listLocal` includes rows under two-part keys and ignores the singleton.

---

## Stream 3 — Conflict flow

Refs: 062 @sync @choice, 072 @errors @surfacing.

### Target design

One interpreter executes a resolution plan, for both the single-planner path
(`usePlannerSave.resolveConflict`) and the batch path
(`useMDUserPlannersData.resolveConflicts`). Identity is minted once, when the plan is built, and a
retry re-interprets the same plan rather than planning again — so a retried "keep both" cannot
create a second copy. The invariant needs an owner or it is prose: **the caller holds the built
plan keyed by the conflict's identity** (a ref in the save hook; a per-item map for the batch
submission), builds it only when a conflict is first presented, and re-interprets the held plan on
every retry; the plan is dropped when its conflict resolves or is dismissed. A fork that saves
locally and then fails to sync is rolled back by deleting the local copy — and a fork whose sync
SUCCEEDED before a later effect failed is rolled back on the server too (`deleteRemote`), because
"nothing the run created survives" is a lie if the server keeps the copy and the next sync pulls
it back. A remote rollback that itself fails is logged and reported with the original failure.

`forkCopy` names its source side explicitly: the interpreter holds both `local()` and `incoming()`
and copies whichever the plan names — a fork effect that silently copies local while the plan says
incoming destroys the very side the user chose to keep.

```ts
// pages/planner/lib/conflictChoice.ts
/** Which step of a resolution failed, and why. `precondition` names a failure before any item
 *  was attempted (a device id that could not be read); it is batch-wide, never per-row. */
export interface ConflictFailure {
  step: 'precondition' | 'validate' | 'saveLocal' | 'sync' | 'deleteLocal' | 'deleteRemote'
  error: AppError
}

/** The effectful operations a resolution needs, injected so the interpreter stays testable. */
export interface ConflictOps {
  /** The two sides of the conflict, read at RESOLUTION time — a mount-time snapshot on either
   *  side replays stale bytes over whatever landed since; a missing row fails closed. */
  local: () => Promise<Result<SaveablePlanner, AppError>>
  incoming: () => Promise<Result<SaveablePlanner, AppError>>
  validate: (planner: SaveablePlanner) => AppError | null
  saveLocal: (planner: SaveablePlanner) => Promise<Result<void, AppError>>
  deleteLocal: (id: string) => Promise<Result<void, AppError>>
  /** Rollback for a fork the server already accepted. */
  deleteRemote: (id: string) => Promise<Result<void, AppError>>
  sync: (planner: SaveablePlanner, force: boolean) => Promise<Result<SaveablePlanner, AppError>>
  sanitizeTitle: (title: string) => string
}

/**
 * The same context value that built `plan`. The interpreter never calls `newId` —
 * reusing one context across build and interpret is what makes "minted once" checkable.
 */
export interface ConflictInterpreterContext {
  newId: () => string
  deviceId: string
  now: string
}

export async function interpretConflictPlan(
  plan: ConflictEffect[],
  ops: ConflictOps,
  ctx: ConflictInterpreterContext,
): Promise<Result<void, ConflictFailure>>
```

Batch resolution stops at the first failure, reports one outcome per item, and removes only the
items that resolved. The dialog stays open showing which item failed rather than clearing
everything or nothing.

```ts
// pages/planner/hooks/useMDUserPlannersData.ts
export interface ConflictOutcome {
  id: string
  result: Result<void, ConflictFailure>
}

resolveConflicts: (resolutions: ConflictResolution[]) => Promise<ConflictOutcome[]>
```

`usePlannerSave.failResolution` (`:773-779`) surfaces every failure kind, not only conflicts. A
non-conflict failure must not close the conflict dialog by displacing the conflict error, so the
hook returns a second slot:

```ts
// PlannerSaveResult
/** Why the last resolution attempt failed. The conflict itself stays in `error`. */
resolutionError: AppError | null
```

`BatchConflictDialog` becomes dismissable and renders per-item outcomes:

```ts
export interface BatchConflictDialogProps {
  open: boolean
  conflicts: ConflictItem[]
  onResolve: (resolutions: ConflictResolution[]) => void
  isResolving?: boolean
  /** One entry per attempted item, in submission order. */
  outcomes?: ConflictOutcome[]
}
```

with an internal `dismissed` flag ANDed into `open`, `showCloseButton` on `DialogContent`, and the
`preventDismissal` handlers (`:186-189`, `:201-203`) removed. The remount key is a **batch epoch**
— a counter that advances only when a new batch arrives (pending conflicts transition from empty
to non-empty) — never the joined id set: keying by ids makes every partial success a remount that
resets the surviving rows' choices to the destructive default, and makes a dismissal under an
unchanged id set unrecoverable. Choices therefore survive partial failure exactly as the user set
them. Dismissal parks, never discards: the list surfaces a visible reopen affordance ("N
unresolved conflicts") while any conflict is pending, and reopening clears `dismissed` without a
new epoch. The dialog's second consumer — the import flow in `PlannerExportImportSection`, which
previously relied on `preventDismissal` — treats dismissal as an explicit cancel: remaining
conflicted planners are skipped, counts reported, and the section returns to idle rather than
wedging in `awaitingChoice`.

`PersonalPlannerList` renders the dialog as a sibling of the list, outside the empty-state branch —
a filtered-to-empty personal list currently returns before the dialog is ever rendered
(`components/plannerList/PersonalPlannerList.tsx:94-96` returning ahead of `:101`), so conflicts are
unreachable exactly when the user filtered.

### Ordered change list

1. `lib/conflictChoice.ts` — add `ConflictFailure`, `ConflictOps`, `ConflictInterpreterContext`,
   `interpretConflictPlan`; `planConflictResolution` and `ConflictEffect` are unchanged.
2. `hooks/usePlannerSave.ts` — `runEffects` (`:733-765`) and `forkLocalChanges` (`:635-702`) are
   replaced by one `interpretConflictPlan` call wired to `storage`/`syncAdapter`;
   `withRollback` moves inside the interpreter.
3. `hooks/usePlannerSave.ts:773-779` — `failResolution` sets `resolutionError` for every kind and
   refreshes `error` only when the failure is itself a newer conflict; add `resolutionError` to
   `PlannerSaveResult` (`:144-171`).
4. `hooks/useMDUserPlannersData.ts:403-480` — `resolveConflicts` calls the interpreter per item,
   stops at the first failure, returns `ConflictOutcome[]`, and removes resolved ids from
   `pendingConflicts` instead of clearing the whole list (`:461`). The bespoke
   `Object.assign(new Error(...))` validation channel (`:391-396`, `:467-477`) is deleted — the
   interpreter's `validate` op reports it as a value.
5. `components/BatchConflictDialog.tsx` — `outcomes` prop, `dismissed` state, `showCloseButton`,
   per-item outcome row; drop `preventDismissal`.
6. `components/plannerList/PersonalPlannerList.tsx` — single return with a ternary; the dialog is a
   sibling of both branches and is keyed by `pendingConflicts.map((c) => c.id).join(',')`.
7. `components/planner/PlannerEditorShell.tsx` — render `resolutionError`; its bespoke
   conflict-failure toast (`:357`) is deleted (stream 4).

### Test plan

- `lib/__tests__/conflictChoice.test.ts` — interpreting the same "both" plan twice calls
  `ops.newId` zero times and produces one fork id; a fork whose `sync` fails calls
  `ops.deleteLocal` with the fork id and reports `{step:'sync'}`; a fork whose rollback also fails
  reports the original `sync` failure, not the rollback's.
- `hooks/__tests__/useMDUserPlannersData.test.tsx` — three resolutions where the second fails:
  outcomes are `[ok, err, …]` with the third never attempted, `pendingConflicts` retains exactly
  the unresolved ids, and the resolved id is gone.
- `components/__tests__/BatchConflictDialog.test.tsx` — the close button dismisses; a new
  `conflicts` array reopens it; a failed outcome renders against its own row.
- `components/plannerList/__tests__/PersonalPlannerList.test.tsx` — with a filter matching nothing
  and two pending conflicts, the dialog is in the document alongside the empty state.

---

## Stream 4 — Error classification and toast consolidation

Refs: 072 @errors @classifier, 072 @errors @presentation, 072 @errors @sink, 045 @errors @boundary.

Order is load-bearing: (a) widens the classifier, (b) builds the presenter on it, (c) makes the
cache the single sink, (d) sweeps the call sites onto that sink, (e) locks the sweep with a rule.
Doing (d) before (c) leaves a window with two sinks and double toasts.

### (a) Classifier

`classifySaveError` moves out of the planner slice and widens from the four cases it recognized to
every error class `lib/api.ts` throws — twelve classes at `:32`, `:49`, `:61`, `:75`, `:86`, `:99`,
`:112`, `:126`, `:139`, `:152`, `:165`, `:178` — plus the storage quota `DOMException`.

```ts
// lib/apiErrorClassifier.ts
/** Every failure the API layer can hand a consumer, as one closed union. */
export type AppError =
  | Tagged<'conflict', { code: string; serverVersion: number | null }>
  | Tagged<'validation', { key: string; params?: Record<string, string> }>
  | Tagged<'restricted', { reason: 'banned' | 'timedOut' }>
  | Tagged<'rateLimit'>
  | Tagged<'forbidden', { code: string }>
  | Tagged<'notFound'>
  | Tagged<'unavailable', { scope: 'service' | 'backend' | 'auth' | 'write' }>
  | Tagged<'retryable'>
  | Tagged<'quota'>
  | Tagged<'unknown'>

export function classifyAppError(error: unknown): AppError

/** A validation failure raised by client-side rules rather than by the API. */
export function validationAppError(friendly: {
  key: string
  params?: Record<string, string>
}): AppError
```

`SaveError` (`pages/planner/lib/plannerSaveErrors.ts:30-36`) is deleted, not adapted: its six cases
are `AppError` cases (`syncPaused` is `{kind:'unavailable', scope:'write'}`, `moderation` is
`{kind:'restricted'}`). Every `Result<_, SaveError>` in `usePlannerSave`, `usePlannerStorage`,
`usePlannerSyncAdapter` and `conflictChoice` becomes `Result<_, AppError>`.

### (b) Presentation

```ts
// lib/errorPresentation.ts
export interface ErrorPresentation {
  key: string
  params?: Record<string, string>
  severity: 'error' | 'warning'
  /** Append the contact-on-repeat description. Opt-in, per error. */
  supportHint: boolean
}

/** How an error is shown, or null when a dedicated surface owns it. */
export function presentError(error: AppError): ErrorPresentation | null

export function showError(error: unknown): void
/** Only the unavailable family, for the query cache's deliberately narrow toasting. */
export function showUnavailable(error: unknown): void
export function showSuccess(key: string, params?: Record<string, unknown>): void
```

`presentError` returns `null` for `conflict` only — and a `null` is a delegation, never a
disposal: every call site funnelling into `showError` that can carry a conflict must either sit
under the mounted conflict dialog or present the conflict itself with a distinct stale-version
message (the two imperative header paths — publish-with-upload and apply-latest-mirror — are the
known such sites). `{kind:'unavailable', scope:'write'}` presents as a warning with its own copy:
no surface owns write-pauses (the sync-paused banner this table once cited never existed), and a
silent failed save is the worst outcome this stream exists to remove. The rule in one line: `null`
requires a named, mounted owner; absent one, the presenter speaks. `supportHint` replaces the
unconditional `contactOnRepeat` description that `lib/toast.ts`'s `Proxy` (`:18-23`) appends to
every error toast; the proxy is deleted, and once the presenter channels absorb the last importer,
`lib/toast.ts` is deleted with it — an unreferenced re-export whose only effect is widening the
import ban's exemption surface fails the deletion test.

### (c) Cache as the single sink

```ts
// lib/queryClient.ts
declare module '@tanstack/react-query' {
  interface Register {
    mutationMeta: {
      successMessage?: string
      successParams?: Record<string, unknown>
      /** Opt out where the mutation renders its own failure surface. */
      suppressErrorToast?: boolean
    }
  }
}

mutationCache: new MutationCache({
  onError: (error, _vars, _ctx, mutation) => {
    if (mutation.meta?.suppressErrorToast === true) return
    showError(error)
  },
  onSuccess: (_data, _vars, _ctx, mutation) => {
    const message = mutation.meta?.successMessage
    if (message !== undefined) showSuccess(message, mutation.meta?.successParams)
  },
}),
```

`handleBackendDownError` (`:19-27`) is deleted; the query cache's `onError` (`:30-39`) keeps its
narrow behavior through `showUnavailable`. A mutation's own `onError` runs **in addition to** the
cache's, never instead of it — that is the double-toast source, and it is why (d) deletes rather
than rewrites the mutation-level error toasts.

### (d) Sweep

Delete — subsumed by the cache sink (13):

| # | Site |
|---|---|
| 1 | `components/hooks/useApiMutation.ts:64` (with the `errorToastKey`/`errorLogPrefix` options) |
| 2–4 | `lib/queryClient.ts:21`, `:23`, `:25` |
| 5 | `pages/moderator/ModeratorPage.tsx:152` |
| 6 | `pages/planner/components/plannerViewer/PublishedPlannerHeader.tsx:145` |
| 7 | `pages/planner/components/plannerViewer/PersonalPlannerHeader.tsx:141` (`toastForError`) |
| 8 | `pages/planner/components/planner/PlannerEditorShell.tsx:357` (stream 3 renders it) |
| 9 | `pages/settings/components/AccountDeleteSection.tsx:62` |
| 10 | `pages/settings/components/LogoutEverywhereSection.tsx:40` |
| 11 | `pages/settings/components/NotificationSection.tsx:60` |
| 12 | `pages/settings/components/SyncSection.tsx:43` |
| 13 | `pages/settings/components/UsernameSection.tsx:53` |

Route through `showError` — catch blocks outside a mutation (11):
`PlannerExportImportSection.tsx:139`, `:173`, `:325`; `PersonalPlannerHeader.tsx:162`;
`useDeckClipboard.ts:52`, `:68`; `CopyUrlButton.tsx:41`; `useCommentMutations.ts:135`, `:165`;
`usePlannerOwnerNotifications.ts:81`; `SyncChoiceDialog.tsx:55`.

Keep as bespoke validation messages — they are not classifications of a thrown error (7):
`PlannerExportImportSection.tsx:191` (extension), `:197` (size); `PlannerEditorShell.tsx:179`
(non-MD type guard); `PersonalPlannerHeader.tsx:194` (`toUserFriendlyError`);
`useDeckClipboard.ts:62` (deck-code validation); `NoteEditor.tsx:304` (URL sanitization);
`useCommentMutations.ts:132`+`:162` (the 409 already-voted / already-reported branches).

Gate the early-firing success toasts (9):

| # | Site | Gate |
|---|---|---|
| 1 | `components/layout/Header.tsx:133` | `meta.successMessage`; the reload follows the toast |
| 2 | `PersonalPlannerHeader.tsx:131` | read the `Result` it currently discards |
| 3 | `NotificationSection.tsx:57` | `meta.successMessage` |
| 4 | `PlannerExportImportSection.tsx:168` | assert the export produced a file |
| 5 | `PlannerExportImportSection.tsx:321` | gate the descriptor on the import counts |
| 6 | `PlannerExportImportSection.tsx:412` | gate the descriptor on the resolve counts |
| 7 | `SyncSection.tsx:40` | assert the new setting from the response, not the intent |
| 8 | `PlannerEditorShell.tsx:330` | gate on `save()`'s boolean |
| 9 | `PlannerEditorShell.tsx:361` | gate on `resolveConflict()`'s boolean |

Also in this stream: `usePlannerVote.ts:96-105` — the `ConflictError` branch gets its real toast
(`comments.toast.alreadyUpvoted`-style key under `planner`), and the stale TODO at `:100-101` is
deleted; `PlannerCardContextMenu.tsx:146-150` — the empty `hasUpvoted` block and its
"Error handled by hook (shows toast)" comment go with it.

### (e) Rule

Bare `toast.error` / `toast.success` / `toast.warning` outside `lib/errorPresentation.ts` and
`lib/toast.ts` fails the build — and so does importing `toast` from `sonner` or `@/lib/toast`
outside those two modules, because a call-shape ban alone is evaded by aliasing, destructuring,
computed member access (`toast[severity]`), or a fresh re-export module. The rule's own tests may
not whitelist a live violation: a `valid:` entry must be a shape the codebase is allowed to keep,
not a shape it happens to contain.

Verify first that oxlint implements `no-restricted-syntax` — run
`yarn --cwd frontend oxlint --print-config` and confirm the rule appears, because oxlint drops
unknown bare rule names silently and a rule that never runs is worse than no rule. If it is absent,
implement the ban as an ast-grep rule pair (`TypeScript` + `Tsx` twins, per stream 7's house
format) under `frontend/scripts/ast-grep-rules/`, which `yarn check:fp` already runs.

### Test plan

- `lib/__tests__/apiErrorClassifier.test.ts` — one case per api error class, twelve assertions plus
  quota and unknown; an unrecognized throw is `{kind:'unknown'}`.
- `lib/__tests__/errorPresentation.test.ts` — `presentError` returns `null` for `conflict` alone
  (write-unavailable presents as a warning); a standing guard walks every `AppError` kind so a new
  case cannot silently join the null set; `supportHint` true adds the contact description and
  false omits it.
- `lib/__tests__/queryClient.test.ts` — a failing mutation toasts exactly once; the same mutation
  with `meta.suppressErrorToast` toasts zero times; `meta.successMessage` toasts on success only.
- A green `yarn check:fp` (or `yarn lint`) with a deliberately introduced bare `toast.error` in a
  page component failing it.

---

## Stream 5 — SSE removal, frontend half

Refs: 073 @sse @scope, 073 @sse @refetch, 042 @sse @exclusion.

### Target design

SSE carries notifications and account-state events only. Planner cache mutation from SSE is gone;
freshness for server-backed planner queries comes from window-focus refetching. The Last-Event-ID
replay contract disappears with the planner events that needed it, and the frontend's SSE event
vocabulary is derived from one list instead of transcribed twice.

```ts
// lib/constants/api.ts
export type SseEventType = (typeof SSE_EVENTS)[keyof typeof SSE_EVENTS]
```

```ts
// shared/sse/schemas/SseEnvelopeSchemas.ts
/** Derived from SSE_EVENTS so the enum cannot drift from the transport's vocabulary. */
export const SseEventTypeSchema = z.enum(
  Object.values(SSE_EVENTS) as [SseEventType, ...SseEventType[]],
)
```

```ts
// shared/sse/hooks/useSseEngine.ts
handlers: Partial<Record<SseEventType, (event: MessageEvent) => void>>
```

`SseStreamCallbacks` gains a status-carrying close. What a 404 means depends on the stream: the
account stream (`/api/sse/subscribe`) names no resource, so its 404s are transient infrastructure
(a routing miss, a deploy window) and keep the normal backoff. The per-planner comment stream can
genuinely lose its resource — a cross-device delete or unpublish makes its open 404 permanent — so
terminal-on-404 is an opt-in engine option (`stopOnNotFound`) that only per-resource consumers set,
paired with a report callback so the stop is said, not silent:

```ts
// shared/sse/lib/sseStream.ts
export interface SseStreamCallbacks {
  onOpen: () => void
  onFrame: (frame: SseFrame) => void
  onRateLimited: (retryAfterMs: number | null) => void
  /** `status` is the HTTP status when the open itself failed, null on a normal end. */
  onClosed: (status: number | null) => void
}
```

### Ordered change list

1. `pages/planner/hooks/useAppSse.ts` — delete `applyPlannerDeleted` (`:124-135`),
   `applyPlannerUpsert` (`:137-148`), `PLANNER_EVENT_APPLIERS` (`:150-156`) and
   `handlePlannerUpdate` (`:158-169`); remove `created`/`updated`/`deleted` from the handlers map
   (`:274-282`). Notification, published, account-suspended and connected handling stays.
2. `pages/planner/schemas/PlannerSchemas.ts:658` — delete `SsePlannerPayloadSchema`; delete its
   tests (`schemas/__tests__/PlannerSchemas.test.ts:390-413`) and the stale comment at
   `hooks/__tests__/useAppSse.test.tsx:248`.
3. `shared/sse/lib/sseStream.ts` — delete `buildHeaders` (`:91-97`) and the `lastEventId` parameter
   of `runSseStream` (`:137-142`); widen `onClosed` to carry the status (`:164-167`).
4. `shared/sse/hooks/useSseEngine.ts` — delete the `lastEventId` local (`:137`, `:165`, `:265`);
   type `handlers` as `Partial<Record<SseEventType, …>>` (`:79`); add the opt-in `stopOnNotFound`
   with its one-shot report callback. The account stream does not set it — its 404s keep the
   backoff path.
5. `lib/constants/api.ts` — delete `SSE_TRANSPORT.LAST_EVENT_ID_HEADER` (`:112`); export
   `SseEventType`.
6. `shared/sse/schemas/SseEnvelopeSchemas.ts:7-17` — derive the enum from `SSE_EVENTS`; reconcile
   the two vocabularies as one (`settings:invalidated` and `connected` both belong;
   `created`/`updated`/`deleted` are retired from `SSE_EVENTS` — the backend no longer has them,
   and a vocabulary value the wire cannot carry is drift by construction).
7. `lib/queryClient.ts:59` — `refetchOnWindowFocus: true`; the local-storage-backed planner queries
   (`useSavedPlannerQuery`, `useMDUserPlannersData`, both `list` and `listFull`) set it back to
   `false` at their own query options, since a focus event tells them nothing.
8. `shared/comment/hooks/usePlannerCommentsSse.ts` — sets `stopOnNotFound` and reports through the
   graceful message keyed under `planner` explaining the planner was removed on another device.
9. `hooks/usePublishedPlannerQuery.ts` — a 404 on the detail open renders the same removal message
   instead of throwing to the route error boundary (074 is silent here; 073 @sse @freshness decides
   it: "a delete performed on another device surfaces as a 404 when the stale entry is opened,
   which the client answers with a message rather than an error boundary").
10. `shared/auth/hooks/useAuthQuery.ts` — a bare network failure (an unwrapped fetch `TypeError`)
    preserves the cached identity exactly as `BackendUnavailableError` does. The focus-refetch flip
    makes transient network blips a routine refetch condition, and answering one with `null` logs
    the user out of the UI and silently disables save sync.

### Test plan

- `shared/sse/hooks/__tests__/useSseEngine.test.ts` — delete the Last-Event-ID replay pin
  (`:238-250`); add: with `stopOnNotFound`, a 404 open produces exactly one report and no reconnect
  attempt; without it, a 404 keeps the backoff path; a dispatched frame whose type is outside the
  vocabulary reaches no handler (the rejection branch itself, not an unhandled known type).
- `pages/planner/hooks/__tests__/useAppSse.test.tsx` — delete the applier cases (`:267`, `:289`,
  `:302`, `:309`, `:329`, `:347`, `:409`); keep gating (`:188-245`) and unmount cleanup
  (`:448-470`); the unparseable and unknown-type cases must exercise a live path — a handled event
  type with an unparseable payload through `handleNotification`'s catch, and a frame rejected by
  the vocabulary gate — not a type nothing handles either way.
- `shared/sse/schemas/__tests__/SseEnvelopeSchemas.test.ts` — the transcribed backend list matches
  `SseEventType.java`'s six `@JsonValue`s in declaration order, plus `connected` (emitted as a
  literal by `AbstractSseService`); the enum-equals-`SSE_EVENTS` assertion is dropped as a
  tautology against its own derivation source.
- A published-planner detail query refetches on window focus; a saved (local) planner query does
  not — asserted against a query client carrying the global `refetchOnWindowFocus: true`, so the
  test proves the override, and covering `listFull` as well as `list`.
- A published-planner detail open answering 404 renders the removal message and does not reach the
  route error boundary.
- `useAuthQuery` answering a fetch `TypeError` keeps the cached identity.

---

## Stream 6 — Editor flush

Refs: 072 @effects @flush.

### Target design

One debounce interval governs both the editor→store hop and the store→IndexedDB autosave, so the
worst-case unflushed window is one interval rather than the sum of two. Both timers flush on
teardown instead of being cancelled: a cancelled timer is silent data loss on unmount and on tab
close. The unload warning is armed from a store subscription, so it is registered whenever state is
dirty rather than whenever the last render happened to observe dirtiness.

```ts
// lib/constants/planner.ts
/** One interval for every editor-side debounce, so a flush window is never additive. */
export const AUTO_SAVE_DEBOUNCE_MS = 200
```

```tsx
// shared/noteEditor/components/NoteEditor.tsx
// The debounce effect keeps cancelling on re-run; a separate unmount-only effect flushes.
const pendingRef = useRef<(() => void) | null>(null)
useEffect(() => () => pendingRef.current?.(), [])
```

**Delivery is pulled, never raced.** Editor-local text and the store are two owners of dirtiness,
and any design that has each editor push its pending text on its own `beforeunload` listener
depends on listener registration order — which, at the `window` target, is the only order there
is: AT_TARGET runs capture and bubble listeners alike in registration sequence, and progressively
revealed sections register after the shell. So editors do not listen for unload at all. A small
registry context holds each mounted editor's `deliverPending` callback; the shell's single unload
handler (registered for `beforeunload` and `pagehide` both) first drains the registry — every
pending delivery lands in the store synchronously — and only then consults `isDirty()` to decide
the warning. Ordering is irrelevant by construction, and an editor's unmount flush is unchanged.

Tiptap's mount-time normalization is delivered synchronously in `onCreate`, not through the
debounce: children's effects run before the parent's, so the store already holds the normalized
document when the save hook's subscription installs the eager baseline — opening a planner is not
a change, arms nothing, and never rewrites a saved row as a draft.

```ts
// pages/planner/hooks/usePlannerSave.ts — replaces the cleanup at :853-858
return () => {
  unsubscribe()
  if (timerRef.current) {
    clearTimeout(timerRef.current)
    timerRef.current = null
    void autoSaveRef.current()
  }
}
```

### Ordered change list

1. `lib/constants/planner.ts:42` — `AUTO_SAVE_DEBOUNCE_MS` becomes `200`.
2. `shared/noteEditor/components/NoteEditor.tsx:80` — the hardcoded `500` becomes
   `AUTO_SAVE_DEBOUNCE_MS`; add the unmount-only flush effect; clear the uncleaned
   `setTimeout(…, 0)` at `:258` while the file is open.
3. `pages/planner/hooks/usePlannerSave.ts:853-858` — flush the armed timer.
4. `pages/planner/hooks/usePlannerSave.ts` — a `dirtyRef` maintained by the existing `subscribe`
   callback (`:843-851`), exposed on `PlannerSaveResult` as a stable
   `isDirty: () => boolean` reader.
5. `pages/planner/components/planner/PlannerEditorShell.tsx:253-265` — register `beforeunload`
   once on mount with an empty dependency list; the handler consults `isDirty()` and
   `isIntentionalNavigationRef` (`:122`).

### Test plan

- `shared/noteEditor/__tests__/NoteEditor.test.tsx` — typing then unmounting before the interval
  elapses still calls `onChange` exactly once with the typed content.
- `pages/planner/hooks/__tests__/usePlannerSave.test.ts` — unmounting with an armed autosave timer
  writes to storage exactly once; unmounting with no armed timer writes zero times.
- `PlannerEditorShell` — a `beforeunload` fired immediately after a store write (before any
  re-render) is prevented; the same event after a save is not.

---

## Stream 7 — Test and fixture safety

Refs: 031 @testing @gates, 028 @toolchain @ci.

### Target design

Test files are type-checked. Fixtures are built by factories that parse through the same schemas
production parses through, so a fixture that drifts from a component's props fails the build rather
than passing a hollow assertion. The two lint gates that exist but never run in CI, run in CI. One
ast-grep rule closes the assertion hole that let a hollow test look green.

`tsconfig.app.json` excludes tests, so a solution-style build is the only way `tsc -b` reaches them.
Composite projects must emit, so declarations go to a temp directory that nothing ships — Vite owns
the real build. The build script therefore compiles only the app project (`tsc -b
tsconfig.app.json`); the bare `tsc -b` solution build belongs to `typecheck` alone, so a test-file
type error can fail the PR gate but never the release artifact.

```jsonc
// frontend/tsconfig.json
{ "files": [], "references": [
  { "path": "./tsconfig.app.json" },
  { "path": "./tsconfig.test.json" },
  { "path": "./tsconfig.node.json" }
] }
```

```jsonc
// frontend/tsconfig.test.json
{
  "extends": "./tsconfig.app.json",
  "compilerOptions": {
    "composite": true,
    "emitDeclarationOnly": true,
    "outDir": "./node_modules/.tmp/types/test",
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.test.tsbuildinfo",
    "types": ["vite/client", "vitest/globals"]
  },
  "include": [
    "src/**/*.test.ts",
    "src/**/*.test.tsx",
    "src/**/__tests__/**/*",
    "src/**/__fixtures__/**/*.json",
    "src/test-utils",
    "vitest.setup.ts"
  ],
  "exclude": [],
  "references": [{ "path": "./tsconfig.app.json" }]
}
```

`"exclude": []` is load-bearing: the inherited app `exclude` filters out exactly the tests this
project includes. The JSON include is load-bearing too — TypeScript 6 defaults `resolveJsonModule`
on, so golden-file imports are project inputs and TS6307 fires without it.

```ts
// src/test-utils/fixtures.ts
/** A floor selection that has survived the same schema production parses through. */
export function buildFloorSelection(
  overrides: Partial<SerializableFloorSelection> = {},
): SerializableFloorSelection {
  return FloorSelectionDraftSchema.parse({
    themePackId: '1001',
    difficulty: DUNGEON_IDX.NORMAL,
    giftIds: [],
    ...overrides,
  })
}
```

```yaml
# frontend/scripts/ast-grep-rules/no-tobedefined-on-queryby.yml
id: no-tobedefined-on-queryby
language: TypeScript
severity: error
message: queryBy* answers null when absent, so toBeDefined() passes on a missing element; assert toBeInTheDocument() or not.toBeNull().
files:
  - src/**/*.test.ts
  - src/**/*.spec.ts
  - src/**/__tests__/**
rule:
  pattern: expect($RECEIVER).toBeDefined()
  has:
    kind: call_expression
    regex: 'queryBy|queryAllBy'
    stopBy: end
```

The `Tsx` twin is byte-identical but for `language: Tsx` and the `.tsx` file globs — `language:
TypeScript` does not match `.tsx`, so a rule without its twin silently ignores every component
test. Validate both patterns with `ast-grep run --debug-query` before committing.

### Ordered change list

1. `frontend/tsconfig.app.json` — `composite: true`, `emitDeclarationOnly: true`,
   `outDir: ./node_modules/.tmp/types/app`; keep the test exclusions and add `src/test-utils` to
   them, so the app project neither compiles test helpers nor emits a second declaration set for
   them.
2. `frontend/tsconfig.node.json` — same composite settings so the solution can reference it.
3. `frontend/tsconfig.test.json` — new, as above.
4. `frontend/tsconfig.json` — becomes the solution file; the current `extends`/`incremental` block
   is deleted.
5. `frontend/package.json` — declare `tsx` and `@ast-grep/cli` as devDependencies (both are
   invoked by scripts today and resolve only from a hoist or from the machine).
6. `src/test-utils/fixtures.ts` — new; `buildFloorSelection`, `buildSaveablePlanner`,
   `buildPlannerSummary`, `buildEgoGiftListItem`, each parsing through its schema; export from
   `src/test-utils/index.ts`.
7. `frontend/scripts/ast-grep-rules/no-tobedefined-on-queryby.yml` and its `-tsx` twin;
   `frontend/scripts/ast-grep-tests/no-tobedefined-on-queryby-test.yml` and the twin's test file.
   Add the missing `queryfn-consumes-signal-tsx-test.yml` while in the directory. The twin carries
   no `ignores:` — a `Tsx` rule cannot match the `.ts` paths its sibling exempts, so a copied list
   is a no-op that reads as a live exemption.
8. `.github/workflows/pr-gate.yml` — the `test-frontend` job (`:202-229`) gains
   `yarn typecheck`, `yarn check:fp`, `yarn check:rules`, and `yarn check:compiler-bailouts`
   steps. `check:rules` is `ast-grep test`: without it the rule tests and snapshots execute
   nowhere, and `ast-grep scan` cannot tell a clean tree from a rule that stopped matching. The
   frontend `build` script's tsc invocation becomes `tsc -b tsconfig.app.json` in the same change,
   per the target design.
9. `frontend/vite.config.ts` — coverage (`:194-204`) gains `thresholds`, an `lcov` reporter, and
   `src/test-utils/**` in `exclude`; the `plugin` project (`:238-245`) gains
   `environment: 'node'`; CI runs `yarn test:coverage`.
10. `pages/egoGift/lib/egoGiftEncoding.ts:26` — export `ENCODED_SELECTION_PATTERN`.
11. `pages/planner/components/plannerViewer/__tests__/ComprehensiveGiftGridTracker.test.tsx` —
    rewritten against the real props (`ComprehensiveGiftGridTracker.tsx:21-29`): the non-existent
    `doneMarks` prop becomes `egoGiftDoneMarks?: Set<string>`, the required `comprehensiveGiftIds`
    is supplied, fixtures come from `buildFloorSelection`, gift ids are schema-valid, and the four
    `expect(container).toBeDefined()` assertions become real ones.

### Test plan

- `yarn --cwd frontend typecheck` fails on a deliberately wrong prop in a test file.
- `pages/egoGift/lib/__tests__/egoGiftEncoding.test.ts` — a pin asserting every string accepted by
  `GiftIdSchema` is also accepted by `ENCODED_SELECTION_PATTERN`, so a widened gift id cannot
  silently become undecodable.
- `ComprehensiveGiftGridTracker.test.tsx` gains a decode-survival positive control: an encoded
  selection built by `encodeGiftSelection` renders its base gift and its enhancement badge.
- `ast-grep test` passes for both twins; a `.tsx` file containing the banned assertion fails
  `yarn check:fp`.

---

## Stream 8 — Mechanical and structural batch

No design content. One row per item: the change, the files it touches, and what proves it.

### Deletions

| Change | Files | Verification |
|---|---|---|
| Delete `usePlannerBookmark` and its test | `pages/planner/hooks/usePlannerBookmark.ts`, `hooks/__tests__/usePlannerBookmark.test.tsx` | No production importer; the doc reference at `usePlannerSubscription.ts:8` goes too |
| Delete the phantom mock of it | `pages/planner/components/plannerList/__tests__/PlannerCardContextMenu.test.tsx:45-46,54,87-90` | The SUT never imports the hook; suite stays green |
| Delete the bookmark write endpoint | `backend/.../planner/controller/PlannerEngagementController.java:82-96`, `PlannerControllerIT.java:1359-1445` | Gate on the legacy-toggle counter at zero; the read projection (`isBookmarked`) is untouched |
| Un-`fixme` or delete the e2e placeholder | `e2e/tests/mutation-gestures.spec.ts:222-226` | Matches whichever way the endpoint goes |
| Delete dead O(n) scans, keep `buildSelectionLookup` | `pages/egoGift/lib/egoGiftEncoding.ts:72-79,89-97`, `pages/egoGift/index.ts:76-77` | `isGiftSelected`/`getGiftEnhancement` have no non-test caller |
| Replace `findEncodedGiftId` in loops with a lookup | `ComprehensiveGiftSelectorPane.tsx:102,129`, `FloorGiftSelectorPane.tsx:113,146` | O(n·m) → O(n); precedent at `EGOGiftSelectionList.tsx:78` |
| Delete extraction dead exports + compat alias | `pages/extraction/lib/extractionCalculator.ts` (17 exports incl. the alias) | Production-dead: only `calculateExtraction` has a non-test caller; `extractionRates.ts:86` is live and stays. Stream 7's golden harness characterizes the dead exports, so this row authorizes deleting those harness cases with their subjects — characterization of deleted code is not coverage |
| Delete the extraction self-barrel import | `pages/extraction/ExtractionPlannerPage.tsx:16` → relative | Slice's public API stops cycling into itself |
| Collapse the duplicated status vocabulary | `lib/constants/planner.ts:105-114`, `PublishedPlannerCard.tsx:10,79-83,131`, `pages/planner/lib/plannerBadges.ts:12-18,28-38` | One table keys one concept |
| Delete the empty `hasUpvoted` block | `plannerList/PlannerCardContextMenu.tsx:146-150` | Stream 4 makes the comment false either way |
| IconFilter, step 1: `CompactIconFilter` gains `layout` + `onClearAll` | `shared/filter/components/CompactIconFilter.tsx:5-18` | Existing 16 consumers unaffected (both props optional) |
| step 2: `EGOGiftKeywordFilter` migrates onto it | `pages/egoGift/components/EGOGiftKeywordFilter.tsx:2,34-53` | Its `h-14` wrapper and "None" child move to the new props |
| step 3: delete `IconFilter` and the duplicate `CompactEGOGiftKeywordFilter` | `shared/filter/components/IconFilter.tsx`, `shared/filter/index.ts:25`, `pages/egoGift/components/CompactEGOGiftKeywordFilter.tsx` | Zero importers after step 2 |
| step 4: rename `CompactIconFilter` → `IconFilter` and `Compact*Filter` → `*Filter` | 16 consumers + `shared/filter/index.ts:9` | "Compact" names nothing once the other is gone |
| Remove unused deps | `frontend/package.json:76` (`radix-ui` umbrella), `:59` (`@tiptap/cli`), `:102` (`@types/dompurify`) | Zero imports; `dompurify@3` ships its own types |
| Move `shadcn` to devDependencies | `frontend/package.json:82` | CLI-only; `components/ui/CLAUDE.md:5` documents the workflow |
| Reconcile the `@babel/core` major split | `frontend/package.json:23` (resolutions `^7`) vs `:89` (devDep `^8`) | Peer of `@rolldown/plugin-babel`; pin one major, do not delete |
| Delete the frontend Dockerfile | `frontend/Dockerfile` | No compose file, CI job, or terraform references it; nginx serves the build |

### Moves

| Change | Files | Verification |
|---|---|---|
| `isRestricted` → `shared/moderation/hooks/useRestrictionStatus` | new hook; `usePlannerSave.ts:869-874,888-889,166-170`; `shared/moderation/components/BanStatusBanner.tsx:19-20`; `shared/moderation/index.ts` | One derivation, two consumers; `usePlannerSave.test.ts:327,335` follow |
| `useUserSettings` + `useFirstLoginStore` → `shared/userSettings` | from `pages/settings/hooks/`, `pages/settings/stores/`; 7 planner importers + `GlobalLayout.tsx:5,8`; `pages/settings/index.ts:1-3` | Breaks the settings↔planner edge; prerequisite for `import/no-cycle: error` |
| `sanityConditionFormatter` → `pages/identity/hooks/` | `pages/identity/lib/sanityConditionFormatter.ts`; `SanityI18n.tsx:9`; 3 test mock paths | A hook does not live in `lib/`; `no-throw-in-lib` scope shrinks correctly |
| Color/class tables → `lib/constants/theme` | `shared/gameData/constants.ts:55,116,336,347,356,376,394`; `lib/constants/layout.ts:22,204`; `lib/constants/planner.ts:105`; `pages/abEvent/lib/abEventTextResolver.ts:11`; `pages/planner/lib/plannerBadges.ts:28` | `lib` may not import `@/shared/*` (`.oxlintrc.json:108-114`), so moved tables carry no gameData types |
| `seasons`/`unitKeywords` module-scope JSON → explicit derived module | `shared/gameData/constants.ts:5-6,410,421`; `SeasonDropdown.tsx:3,30`; `UnitKeywordDropdown.tsx:3,38` | Two JSON bundles leave the main chunk; lazy precedent at `useFilterI18nData.ts:17,26` |
| `PlannerSection` → `components/layout/` | `pages/planner/components/PlannerSection.tsx`, `pages/planner/index.ts`, ~15 in-slice importers, `ExtractionCalculator.tsx:19` | Only deps are `components/ui/button` + `SECTION_STYLES`; kills a cross-slice reach |
| `classifySaveError` → `lib/apiErrorClassifier.ts` | per stream 4 | — |

### Injections

| Change | Files | Verification |
|---|---|---|
| `DetailEntitySelector` takes `tierIconPath` and bounds as props | `components/layout/DetailEntitySelector.tsx:15-32,64-65,68,114` | Component stops knowing entity types; delete the never-destructured `disabledTiers` (`:29`) |
| `EntityMetaInfo` takes resolved labels | `components/layout/EntityMetaInfo.tsx:7-12,26-32` | Drops the suspending `useFilterI18nData` from a leaf |
| `editorStateCodec` takes `maxThreadspin` injected | `pages/planner/lib/editorStateCodec.ts:9,24,28` | Kills the static `egoSpecList` import and the unchecked cast; precedent `deckCode.ts:168` |

### Bundles

| Change | Files | Verification |
|---|---|---|
| `ActionDialogBaseProps` (8 shared fields); `ModerationReasonDialog` composes it | `components/feedback/ConfirmActionDialog.tsx:14-38`; `shared/moderation/components/ModerationReasonDialog.tsx:8-25`; 7 wrapper dialogs | Wrappers stop re-declaring `open`/`onOpenChange`/`isPending`/`onConfirm` |
| `CatalogSlice<TItem>` with an asymmetric ego extension | `pages/{identity,ego,egoGift,abEvent,themePack}/components/*List.tsx`; the three `EntityListDataConfig` instances | EGO alone carries `maxThreadspin` and its own sorter; the extension is the asymmetry, not a special case |
| `ThemePackDescriptor` replaces loose theme-pack params | `pages/planner/lib/floorGiftBucketing.ts:20-25`; `FloorGiftSelectorPane.tsx:21-29,89,94-96`; `HorizontalThemePackGallery.tsx:16-19` | `FloorThemeSelection` (`pages/themePack/types/ThemePackTypes.ts:13`) already is the descriptor |
| `Partial<DeckBuilderActions>` on `DeckBuilderSummary` | `deckBuilder/DeckBuilderSummary.tsx:14-27`; `deckBuilder/DeckBuilderContent.tsx:35-41` | Three optionals stop duplicating the action type |
| 9-prop filter bundle from `useMDGesellschaftFilters` | `plannerList/PublishedPlannerList.tsx:15-38,65-75`; `hooks/useMDGesellschaftFilters.ts:31-60` | The 8 filter props are already re-spread verbatim into the data hook |
| `CardGeometry` on `FilteredEntityGrid` | `shared/filter/components/FilteredEntityGrid.tsx:13-35,84-88,97-99,127-136` | `cardWidth`/`cardHeight`/`mobileScale`/`fixedRowHeight` are threaded twice today |
| `ExtractionScenario` replaces the 5-param bundles | `pages/extraction/lib/extractionCalculator.ts:247-253,342-348,376-382` | Three orderings of the same five values, side by side at `lib/__tests__/extractionMatrix.ts:186-202` |

### Generalizations

| Change | Files | Verification |
|---|---|---|
| `useUrlFilters<T>` | new; replaces `useMDGesellschaftFilters.ts:89`, `useMDUserFilters.ts:70`, `usePlannerSearchFilters.ts:89` | All three repeat `useSearch({strict:false}) as T \| undefined` + defaults-omitted `setFilters`; the unchecked cast (`useMDGesellschaftFilters.ts:92`) dies once |
| `createEntityMatcher` for the five `matchesX` | `identityFilter.ts:62`, `egoFilter.ts:55`, `egoGiftFilter.ts:94`, `themePackFilter.ts:53`, `keywordFilter.ts:48` | `matchesAbEvent` (no `searchTerms`) and `matchesDeckFilter` (raw state) stay out |
| `SelectorPaneShell` from `DeckBuilderPane` + `useCappedSelection` | `deckBuilder/DeckBuilderPane.tsx:8-12`; `startGift/StartGiftEditPane.tsx:46-55,122,144,190`; `startGift/StartGiftRow.tsx:15,77` | Cap logic exists twice; the dead `maxSelectable` prop (`EGOGiftSelectionList.tsx:18,37`) and its 4 call sites go |
| Static-i18n hooks onto `useEntityListData` configs | `useTraitsI18n.ts`, `useFilterI18nData.ts`, `useSearchMappings.ts`, `useSkillTagI18n.ts`, `useKeywordListData.ts`, `useBattleKeywords.ts`, `useSanityConditionData.ts`, `useColorCodes.ts`, `usePlannerKeywordsI18n.ts` | — |
| Remove `keepPrevious` everywhere; rewrite the rule to name skeletons + Deferred hooks | `lib/queryOptions.ts:9,12,50`; the 6 `{keepPrevious:true}` callers; `src/pages/CLAUDE.md:15` | Inert under Suspense — the boundary shows the skeleton, so a placeholder is never rendered |
| `FILTER_SECTIONS` registry; delete dead `defaultExpanded` | `shared/filter/components/FilterSectionList.tsx:14-36,52-79,102`; `FilterSection.tsx:4,14`; 16 call sites in `EGOPage.tsx`/`IdentityPage.tsx`, plus `DeckFilterBar.tsx:374` | The prop is passed 17 times and destructured zero times |
| `unitKeywords` single owner hook | `shared/gameData/constants.ts:6,421`; `useSearchMappings.ts:37`; `useFilterI18nData.ts:26`; `useTraitsI18n.ts:16` | Four readers, one of them eager and EN-only |
| `invalidatePlannerLists` called by publish/delete/vote/fork | new helper; `usePlannerPublish.ts:69`, `usePlannerDelete.ts:65-66`, `usePlannerHeaderActions.ts:72,113-114`, `usePlannerVote.ts:81,94`, `usePlannerFork.ts:155`, `useModeratorPlannerDelete.ts:14-19`, `PublishedPlannerHeader.tsx:131-135`, `PersonalPlannerHeader.tsx:127` | Nine sites, nine different key sets today |

### Branded entity-id primitives and schema composition

`shared/gameData/ids.ts` declares one branded primitive per value-role entity id. Value-role ids are
numbers in the source data; key-role record keys and route params stay plain `string`, converted once
where list items are constructed (per the sweep: entity ids as referenced values are `z.number()`
throughout the data — the brands make transposition a compile error, per the `PlannerIdSchema`
precedent at `schemas/PlannerSchemas.ts:566`).

```ts
// shared/gameData/ids.ts
export const IdentityIdSchema = z.number().int().brand<'IdentityId'>()
export const EGOIdSchema = z.number().int().brand<'EGOId'>()
export const EGOGiftIdSchema = z.number().int().brand<'EGOGiftId'>() // base id, no enhancement prefix
export const PassiveIdSchema = z.number().int().brand<'PassiveId'>()
export const SkillIdSchema = z.number().int().brand<'SkillId'>()
export const ThemePackIdSchema = z.number().int().brand<'ThemePackId'>()
export const SeasonSchema = z.number().int().brand<'Season'>()

export type IdentityId = z.infer<typeof IdentityIdSchema>
// ... one type per schema
```

Composition rules, in dependency order:

1. Entity slices' Zod schemas reference these primitives for every value-role field (recipe
   materials, passive lists, theme-pack pools, `FeaturedBoss.unitId`), replacing bare `z.number()`.
   The two realignments from the sweep (ego passive ids, `EGOGiftSpec.themePack` — string→number,
   data included) land first so every value-role field has a numeric source.
2. String wire forms compose from the primitives, not from local regexes: `shared/gameData/ids.ts`
   also exports the base string patterns (`GIFT_ID_PATTERN` = the `9\d{3}` base), and
   `schemas/PlannerSchemas.ts:42-70` re-derives its `IdentityIdSchema`/`EGOIdSchema`/`GiftIdSchema`/
   `ThemePackSchema` string regexes from them instead of restating the formats — closing the
   divergence between the planner's gift regex and `egoGiftEncoding`'s pattern at the source.
   `ENCODED_SELECTION_PATTERN` (`pages/egoGift/lib/egoGiftEncoding.ts:26`) derives its body from the
   same exported base, and the superset pin test (stream 7) locks the relation.
3. Adoption is boundary-only: parse at list-item construction and Zod ingest; components receive
   already-branded values and never call `Number()`/`String()`/`parseInt` on ids (the ~25 scattered
   coercion sites collapse into the constructors named in the fixlet row below).
4. Planner UUIDs (`PlannerIdSchema`, already branded, 33 bypasses to adopt) and encoded gift
   selection ids (family c — strings by design) are out of scope for numeric branding.

### Correctness fixlets

| Change | Files | Verification |
|---|---|---|
| `GIFT_UNKNOWN_ID` splits into a floor-scoped code with emitter context | `lib/plannerValidationErrors.ts:56,83,159`; `lib/plannerValidation.ts:334,654` | Removes the `as FloorValidationError` cast; the non-floor emitter stops producing empty `floor`/`gifts` params |
| Difficulty switch → per-category table keyed by `FLOOR_COUNTS` | `lib/plannerValidation.ts:575-623`; `shared/gameData/constants.ts:365-369` | Four branches push one identical error; only the allowed set differs |
| Gate the per-render stringify behind the store subscription | `hooks/usePlannerSave.ts:258,863-867` | Full planner JSON on every render today; stream 6's `dirtyRef` supplies the answer |
| `setComprehensiveGift(base, enhancement)` shared by the three panes | `stores/usePlannerEditorStore.tsx:97,217-218`; `StartGiftEditPane.tsx:49-77,80-97,100-128,150-160`; `EGOGiftObservationEditPane.tsx:103-125,148-157`; `ComprehensiveGiftSelectorPane.tsx:93-138,153-155` | Whole-Set replace forces four hand-written mirrorings per pane |
| Rename `sortGiftSelections` to name what it does; add the merged variant | `pages/egoGift/lib/egoGiftEncoding.ts:209-217`; `pages/egoGift/index.ts:82`; `ComprehensiveGiftSummary.tsx:78`; `FloorGiftViewer.tsx:54` | It re-pairs enhancement levels onto `sortEGOGifts` output; it does not sort selections |
| Branded entity-ID schemas adopted at list-item construction | new `shared/gameData/ids.ts`; `ComprehensiveGiftSelectorPane.tsx:58-70`, `EGOGiftObservationEditPane.tsx:52-63`, `FloorGiftSelectorPane.tsx:71`, `EGOGiftObservationSummary.tsx:56`, `EGOGiftObservationSelection.tsx:29` | Five hand-rolled constructors; one omits `recipe` |
| Ego passive ids and `EGOGiftSpec.themePack` become numbers | `pages/ego/schemas/EGOSchemas.ts:57-66,70,81,111`; `pages/egoGift/schemas/EGOGiftSchemas.ts:48,65`; `pages/egoGift/types/EGOGiftTypes.ts:36`; `static/data/ego/*.json` (111 files, 116 entries); `static/data/egoGiftSpecList.json` (230 records) | Skill ids are already numbers in the same files; i18n keys stay strings |
| i18n key fixes | `ThemePackTrackerCard.tsx:80-81`, `ComprehensiveGiftGridTracker.tsx:284-285` (add the missing `markAsDone`/`markAsNotDone` keys); `CopyUrlButton.tsx:41` (`common:error` resolves to the literal `error`); `CommentEditor.tsx:45` (uses `error` as a namespace) | The two usages of `error` are mutually incompatible; pick one shape |
| Rename the `t` shadow | `components/layout/DetailEntitySelector.tsx:97,111,115` | Unblocks the i18n `t` for the two hardcoded `Tier ${t}` strings |
| Restore the `.env` ignores | `frontend/.gitignore:27` | The line reads `.env.env.production` — a bad append onto an unterminated final line that also destroyed the original `.env` entry; the pattern matches no file, so both `.env` and `.env.production` are committable today |
| Drop the `association` namespace | `components/layout/Header.tsx:183`; `lib/i18n.ts:14` | `useTranslation(['common', 'association'])` requests a namespace that exists in no locale directory and is absent from `NAMESPACES` |
| Enable `jsx-a11y`; label 5 icon-only buttons | `.oxlintrc.json:3`; `Header.tsx:164`; `CommentActionButtons.tsx:167-169`; `PlannerDetailFooter.tsx:88-113`; `PersonalPlannerHeader.tsx:247-253`; `PublishedPlannerHeader.tsx:198-201,279-286` | `hidden lg:inline` labels are `display:none` below `lg`, so those buttons have no accessible name on mobile |

### Security and ingest hardening

| Change | Files | Verification |
|---|---|---|
| `sanitizeUserHtml` wrapper with the tiptap-schema allowlist (`FORBID_ATTR: ['style']`, no `form`/`input`/`style` tags); `sanitizeToPlainText` absorbs the existing `ALLOWED_TAGS: []` call; oxlint `no-restricted-imports` bans bare `dompurify` outside `shared/sanitize/` | new `shared/sanitize/`; `shared/comment/components/CommentCard.tsx:57`; `PlannerExportImportSection.tsx:60`; `.oxlintrc.json` | Default DOMPurify keeps `<style>` and styled full-viewport anchors — click-hijack on public planner pages; backend sanitization of comment content is a tracked `PENDING` exemption, so this is the standing defense |
| Bound both decompressions: chunked inflate aborted past `EXPORT_MAX_FILE_SIZE * 20` output; clipboard deck codes rejected past a small length constant before `atob` | `pages/planner/lib/plannerExportImport.ts:108`; `pages/planner/lib/deckCode.ts:180,308`; `lib/constants/planner.ts` | pako has no `maxOutputLength`; the import gate bounds compressed bytes only, and the clipboard path bounds nothing while a real deck code is ~150 chars |
| Skill-EA values coerced to numbers at ingest with a read-time reconciler; numeric guard before `total +=` | `pages/planner/lib/editorStateCodec.ts:208` (bare `??` on `skillEAState` while every sibling field re-checks); `lib/plannerValidation.ts:290` | No production path enforces number values — the enforcing schema is test-only; a stored `"3"` concatenates to `total = "0321"` |
| `usePlannerCommentsSse` payload validated with `CommentNodeSchema.safeParse`, bail on failure | `shared/comment/hooks/usePlannerCommentsSse.ts:105-115` | `payload as CommentNode` guarded only by `typeof added.id`; the sibling SSE handlers all `safeParse` |
| `usePublishedPlannerQuery` routes `JSON.parse(content)` through draft-mode validation instead of `as MDPlannerContent` / `as MDCategory` casts | `pages/planner/hooks/usePublishedPlannerQuery.ts:65,87-93`; `schemas/PlannerSchemas.ts:436` | `validateSaveablePlanner` and the content field schemas are production-dead today — this is their first live ingest call site |
| Latent-cast hardening: RR save branch fails fast instead of `as unknown as RRCategory`; `api.ts` caller headers via `new Headers(options.headers)`; `featuredCount <= 0` returns `Infinity` expected-pulls; sync-adapter/fork `as PlannerEditorConfig` ingest goes through the same draft validation; ast-grep rule bans `as unknown as` on ingest paths (TypeScript + Tsx twins) | `hooks/usePlannerSave.ts:247-252`; `lib/api.ts:229-238`; `pages/extraction/lib/extractionCalculator.ts:793`; `hooks/usePlannerSyncAdapter.ts:38,58-61`; `hooks/usePlannerFork.ts:111,130-133`; `scripts/ast-grep-rules/` | Each is guarded one layer away today (schema-on-write, UI clamps, object-literal callers); the rule is what prevents the guards and the casts drifting apart |
| Type the vote cache updater | `hooks/usePlannerVote.ts:81-91` | `(old: any)` erases the `apiData` guard; field names match `PublicPlannerSchema` only by coincidence of no rename yet |

### Consolidation residue

| Change | Files | Verification |
|---|---|---|
| `ModeratorPage`: staff gate moves ahead of the suspense hooks (`enabled`/route-level guard); one mutation-hook set per table, not per row | `pages/moderator/ModeratorPage.tsx:274-289,126-234` | Non-staff visitors currently suspend into a 403 boundary before the access-denied panel can render; `UserRow` mounts 4×N mutations and dialogs |
| One relative-time owner in `lib/formatDate.ts` (Intl), one internal locale resolver; delete `formatShortRelativeTime`, `LastSavedLabel`'s date-fns import, and the `date-fns` dependency | `lib/formatDate.ts:61-204`; `lib/utils.ts:3,210`; `components/planner/LastSavedLabel.tsx:2-11`; `package.json` | Three implementations across two libraries with three locale maps; adjacent surfaces render different locales today |
| Boundary-validation idiom converges on `validateData`/`validateDataOrNull`; the five non-error `console.log` calls are removed | `pages/planner/lib/plannerApi.ts:39-139`; `hooks/useAppSse.ts`; `hooks/usePlannerStorage.ts`; `hooks/useMDUserPlannersData.ts:249,273`; `GlobalLayout.tsx:49` | 21/11/9 three-way split today; one SSE file uses two idioms at two log severities for identical contract violations |
| Settings hooks un-collide: the hook named `useUserSettingsQuery` moves into the file of that name or both rename by domain; one query-key export name | `pages/settings/hooks/useUserSettings.ts:23`; `useUserSettingsQuery.ts` | The barrel re-exports the hook from the file that does not bear its name; two near-identical key factories coexist |
| Dual named+default exports drop the default on the 16 non-page components | `DetailRightPanel.tsx:27`, `Toolbar.tsx:179`, `SinnerGrid.tsx:113`, +13 | Default stays reserved for `lazyRouteComponent` pages; only 3 non-test default imports exist |
| `DeckFilterBar` derives count and reset from its `FILTERS` registry; the codec's fourth copy reads the same registry | `pages/planner/components/deckBuilder/DeckFilterBar.tsx:166,261-272,287-302`; `lib/editorStateCodec.ts:106-121` | The ten filter sets are enumerated four times; the variadic counter silently under-counts a missed set |
| `usePlannerSave` decomposition: restriction status out (per the `shared/moderation` move), sync/conflict behind their own seam, `buildSaveable(status, published)` collapsing the `performSave`/`autoSave` preamble; `validateBeforeSync` returns `Result` instead of the throw-decorated `Error`; `runSync` splits into named pull/purge/conflict-collect functions in `lib/syncPlan` | `hooks/usePlannerSave.ts` (891 lines, 12-field options/13-field result); `hooks/useMDUserPlannersData.ts:227-317,370-397` | The five-concern fusion and the 11-field duplicated preamble are the two largest remaining god-shapes after streams 1-6 land |
| Import/export handlers split into decode → partition → persist phases in `lib/plannerExportImport.ts`; `AccountDeleteSection`'s 34-line mutation callback delegates to a `useDeleteAccountFlow` hook; `NoteEditor.handlePaste` policy moves onto its imported primitives in `lib/noteUtils.ts`; the enhancement-toggle/recipe-cascade block dedupes into one `applyGiftToggle` | `PlannerExportImportSection.tsx:97-329`; `settings/AccountDeleteSection.tsx:32`; `shared/noteEditor/components/NoteEditor.tsx:132-166`; `floorTheme/FloorGiftSelectorPane.tsx:102-156` + `ComprehensiveGiftSelectorPane.tsx:93-138` | The remaining round-one logic/view extractions with existing tested lib/ homes; the export path's inline `try/finally` is a compiler-bailout allowlist entry that this removes |
| Hardcoded `alt` literals through `t()` or `alt=""` | `DetailEntitySelector.tsx:115`; `EGOCard.tsx:73`; `AllEnhancementsPanel.tsx:60`; `CostDisplay.tsx:11`; `SkillInfoPanel.tsx:95`; `SkillImageComposite.tsx:76`; `SkillImageSimple.tsx:57`; `ResistancePanel.tsx:28,39,50` | Decorative glyphs take `alt=""`; meaningful ones take a key |
| Set `components.json` hooks alias | `frontend/components.json:18` → `@/components/hooks` | `@/hooks` is not a real directory |
| Drop the stale oxlint exemption | `.oxlintrc.json:119-132` — remove `RouteErrorComponent.tsx` from `files` | It imports no `@/pages/*` or `@/shared/*` |
| Delete the duplicate `cn` | `shared/noteEditor/lib/tiptap-utils.ts:20`; `components/tiptap-ui-primitive/button/button.tsx:7` → `@/lib/utils` | The local copy has no tailwind-merge, so conflicting classes are not deduped |
| Split `api.ts`; hoist 401 eviction to the auth layer | `lib/api.ts:2,259-262` (358 lines) → `lib/apiErrors.ts` + the eviction at the auth boundary; `lib/queryClient.ts:3-8` | Breaks the hard `api.ts` ↔ `queryClient.ts` cycle; the hand-rolled `['auth','me']` key becomes the factory's |
| Split `router.tsx` | `lib/router.tsx` (587 lines): 9 loaders (`:187-412`) → `routeLoaders.ts`; the language listener (`:569-580`) → `routerTitle.ts`; the MD search schemas (`:63-101`) derive from `MD_CATEGORIES` (`shared/gameData/constants.ts:182`) | `.oxlintrc.json:133-138` already permits the canonical import here |
| `import/no-cycle` → `error` | `.oxlintrc.json:78` | The live cycle set at flip time was the `api.ts`↔`queryClient` pair (5), a `gameText`↔`skill` SCC (9) cut by moving `useSkillTagI18n`/`SkillTagSchemas` into `shared/gameText`, and a tiptap self-barrel (2); the four originally named prerequisites had already gone quiet under earlier streams. No warning-denying lint flag — 149 unrelated warnings predate it |
| ErrorBoundary alias convention | `CommentEditor.tsx:14`; `NoteEditor.tsx:3`; `ExtractionPlannerPage.tsx:15`; `EGOGiftTooltipContent.tsx:3` | Library import is visually identical to the project wrapper's today |
| Parameterize the three keyword backlink lists | `pages/keyword/KeywordDetailPage.tsx:58-92,98-132,138-167,191-197,228-230` → `components/KeywordBacklinkList.tsx` | Label key, name hook, route, and label formatter are the only differences |
| Skeleton reuse | `plannerViewer/TrackerModeViewer.tsx:193-205` → `SkillGridSkeleton` (`plannerSkeletons.tsx:81-102`); `home/components/RecentlyReleasedSection.tsx:205-240` reuses the card shell; `components/feedback/DetailPageSkeleton.tsx` becomes a layout-only shell with per-slice presets moved into the slices | `GuideModeViewer.tsx:144` and `PlannerEditorShell.tsx:497` already use the shared one |
| `StartBuffCard.onSelect(id, selected)` | `startBuff/StartBuffCard.tsx:191-205,268-274` | Replaces `onSelect(-id)` sign encoding the prop type does not express |
| Extract `CARD_VARIANTS` and fix the inert class | `startBuff/StartBuffCard.tsx:53-104,86,109-119,232-233` | `text-[${MD_ACCENT_COLORS[7]}]` is runtime-interpolated, so Tailwind never generates it; `:328` shows the inline-style form that works |
| `noUncheckedIndexedAccess` + `exactOptionalPropertyTypes` | `frontend/tsconfig.app.json:23-29` | Surfaces `plannerValidation.ts:319`, `StartBuffCard.tsx:233`, `DetailEntitySelector.tsx:71`, `api.ts:21`; land after stream 7 so tests are checked too |


---

## Rollout

**Inside the big-bang window** — these change a wire contract or an on-disk format and must land
with their counterparts, in this order:

1. Stream 1 with RFC 0003 stream 1. The `.strict()` schemas tolerate the transitional
   `contentDigest` key as optional while the backend still emits it; the backend's wire cleanup
   (withdrawing the field) and the batch endpoint are the coupled halves. The frontend must not
   land its optional-field removal before the backend stops emitting the key.
2. Stream 2's `DB_VERSION` 2 migration. It is not reversible: a client that has upgraded and then
   loads an older bundle finds two-part keys under a v1 reader. A rollback of the frontend after
   this ships strands local planners.
3. Stream 5 with RFC 0003's SSE half. Removing planner appliers before the backend stops emitting
   planner events is safe (unknown types dispatch to nothing); removing the Last-Event-ID header
   before the backend stops requiring it is not. Header removal follows the backend's.

**Freely landable, any order:**

- Stream 3 — behavior-only, no wire or storage change. Depends on stream 1's `AppError` only if
  stream 4(a) has already landed; otherwise it uses `SaveError` and is retyped by 4(a).
- Stream 4 — internal to the frontend. Sub-order (a)→(b)→(c)→(d)→(e) is mandatory within it.
- Stream 6 — behavior-only.
- Stream 7 — tooling; land early, because it is what makes the other streams' tests type-check.
- Stream 8 — one row at a time, except the four IconFilter steps and the four `import/no-cycle`
  prerequisites, which are ordered within their rows.

Land stream 7 first if anything is landed out of order: every other stream's test plan assumes
fixtures that type-check.

**Stream 7b — pre-existing test-type repair.** The first solution build over the test surface
exposed 169 pre-existing type errors across 41 test files that no stream's change list claims;
roughly a dozen of those files belong to no stream at all, so per-stream fixing cannot ever turn
the new CI typecheck step green. A dedicated node lands immediately after stream 7 and fixes all
of them mechanically — hollow fixtures completed, unsafe mock casts typed — except
`usePlannerSave.test.ts` and `usePlannerSyncAdapter.test.ts`, which stream 1's change list already
rewrites under its own authorization. Behavior-preserving: no assertion may weaken, and the
`tests/` diffstat stays non-negative.

---

## Acceptance checklist

- [ ] The client presents and adopts `syncVersion` only; no digest is computed, stored, or
      compared client-side, and the tolerated wire field is never read.
- [ ] `presentedVersion` never decreases; `adoptAck` decreases it only at the two sites that adopt
      server content in the same step, and only after the local write is confirmed; the
      pre-force-push read is forward-only.
- [ ] Seed and exit defaults agree on `INITIAL_SYNC_VERSION`; no code path can present `0`.
- [ ] `categorizePlanner` decides per row on version and status; `categorizeSync` keeps its
      three-way partition.
- [ ] `runSync` fetches its pull residue through the batch endpoint, chunked to
      `BATCH_PULL_MAX_IDS`, issuing no request for an empty residue.
- [ ] `storage.getItem` distinguishes absence from failure; a failed open does not poison later
      reads; `getOrCreateDeviceId` never mints on a failed read.
- [ ] Keys are `planner:{id}` at all six sites; `loadPlannerTitle` uses the builder; the v1→v2
      migration is copy-verify-delete inside the versionchange transaction, newest-wins on
      collision, and the device-id singleton survives.
- [ ] One interpreter runs both sync-path conflict plans; the plan is held by its caller and a
      retry re-interprets it, never re-plans; identity is minted once; a fork whose sync fails is
      deleted locally, and one the server accepted is deleted remotely on rollback. (The import
      flow's hand-rolled executor is recorded debt, not silent divergence.)
- [ ] Batch resolution stops at the first failure, removes only resolved items, and surfaces the
      failure; the dialog can be closed; it is reachable from a filtered-empty personal list.
- [ ] Non-conflict resolution failures reach the user without displacing the conflict.
- [ ] `AppError` covers all twelve api error classes plus quota; `SaveError` is gone.
- [ ] Exactly one toast per failed mutation; `contactOnRepeat` appears only where `supportHint` is
      set; success toasts fire only after a confirmed result.
- [ ] A bare `toast.error`/`toast.success` outside the two sanctioned modules fails the build, and
      the rule is proven to run (not silently unknown).
- [ ] No SSE handler mutates a planner cache; no Last-Event-ID is sent; the FE event enum is
      derived from `SSE_EVENTS` and carries no value the wire cannot; server-backed planner
      queries refetch on focus and local ones do not; a per-resource stream's 404 reports once and
      stops, the account stream's keeps backoff; a deleted planner's detail open answers with the
      removal message, not the error boundary.
- [ ] One debounce constant; both the editor and the autosave flush on teardown; the unload warning
      is armed from the store subscription.
- [ ] `tsc -b` type-checks tests and `test-utils`; fixtures parse through production schemas; both
      ast-grep twins ship with tests; `typecheck`, `check:fp`, `check:compiler-bailouts` and
      coverage thresholds run in the frontend CI job.
- [ ] `ComprehensiveGiftGridTracker.test.tsx` uses the real props, schema-valid fixtures, real
      assertions, and a decode-survival positive control.
- [ ] Every stream 8 row is landed or explicitly deferred with a reason in `docs/debt.md`.
- [ ] `import/no-cycle` is an error and the build is clean.

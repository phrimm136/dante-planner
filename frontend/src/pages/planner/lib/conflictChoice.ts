import { assertNever } from '@/lib/utils'
import { ok, err } from '@/lib/result'
import { withRollback } from '@/lib/withRollback'

import type { Result } from '@/lib/result'
import type { AppError } from '@/lib/apiErrorClassifier'
import type {
  ConflictResolutionChoice,
  PlannerStatus,
  SaveablePlanner,
} from '../types/PlannerTypes'

/**
 * Success toast for each conflict resolution, keyed so a new choice cannot ship
 * without one.
 */
export const CONFLICT_TOAST_KEY: Record<ConflictResolutionChoice, string> = {
  overwrite: 'planner:pages.plannerMD.conflict.overwriteSuccess',
  discard: 'planner:pages.plannerMD.conflict.discardSuccess',
  both: 'planner:pages.plannerMD.conflict.keepBothSuccess',
}

/** Sync version a freshly forked copy starts from. */
const FORK_SYNC_VERSION = 1

/** The conflicting planner, as far as the resolution decision needs it. */
export type PlannerConflict = {
  /** Side that becomes a separate planner when both versions are kept. */
  forkSide: 'local' | 'incoming'
  /** Title of that side, already resolved to a placeholder when it is empty. */
  forkTitle: string
}

/** Inputs a resolution cannot derive, injected so the decision stays pure. */
export type ConflictResolutionContext = {
  deviceId: string
  /** ISO 8601 timestamp stamped on the copy. */
  now: string
  newId: () => string
  /** Titles the copy; translation stays with the caller. */
  copyTitle: (title: string) => string
}

/** Identity a kept-both copy takes on, replacing the metadata it was forked from. */
export type ConflictForkMetadata = {
  id: string
  title: string
  status: PlannerStatus
  syncVersion: number
  deviceId: string
  createdAt: string
  lastModifiedAt: string
  savedAt: string
}

/**
 * One step of a resolution. `keepLocal` and `adoptIncoming` name which side ends
 * up under the conflicting planner id; each caller executes them against its own
 * storage, which may already hold that side and write nothing.
 */
export type ConflictEffect =
  | { kind: 'keepLocal' }
  | { kind: 'adoptIncoming' }
  | { kind: 'forkCopy'; metadata: ConflictForkMetadata }

function forkMetadata(
  conflict: PlannerConflict,
  ctx: ConflictResolutionContext,
): ConflictForkMetadata {
  return {
    id: ctx.newId(),
    title: ctx.copyTitle(conflict.forkTitle),
    status: 'saved',
    syncVersion: FORK_SYNC_VERSION,
    deviceId: ctx.deviceId,
    createdAt: ctx.now,
    lastModifiedAt: ctx.now,
    savedAt: ctx.now,
  }
}

/** Which step of a resolution failed, and why. */
export interface ConflictFailure {
  step: 'validate' | 'saveLocal' | 'sync' | 'deleteLocal'
  error: AppError
}

/** How one submitted resolution ended, for the surface that listed the conflicts. */
export interface ConflictOutcome {
  id: string
  result: Result<void, ConflictFailure>
}

/** The effectful operations a resolution needs, injected so the interpreter stays testable. */
export interface ConflictOps {
  /** The side that ends up under the conflicting id when local wins. */
  local: () => SaveablePlanner
  /** The server's side, read here when the caller does not already hold it. */
  incoming: () => Promise<Result<SaveablePlanner, AppError>>
  validate: (planner: SaveablePlanner) => AppError | null
  saveLocal: (planner: SaveablePlanner) => Promise<Result<void, AppError>>
  deleteLocal: (id: string) => Promise<Result<void, AppError>>
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

function failed(step: ConflictFailure['step'], error: AppError): Result<never, ConflictFailure> {
  return err({ step, error })
}

/** Force-push the local side, then store what the server acknowledged. */
async function keepLocal(
  ops: ConflictOps,
  ctx: ConflictInterpreterContext,
): Promise<Result<void, ConflictFailure>> {
  const planner = ops.local()

  const invalid = ops.validate(planner)
  if (invalid) return failed('validate', invalid)

  const synced = await ops.sync(planner, true)
  if (!synced.ok) return failed('sync', synced.error)

  // The server echoes the status it was sent, so the resolution marks the row
  // saved itself. Left a draft it would stay badged unsaved and turn the next
  // server-version bump into another conflict prompt.
  const saved = await ops.saveLocal({
    ...synced.value,
    metadata: { ...synced.value.metadata, status: 'saved', savedAt: ctx.now },
  })
  return saved.ok ? ok(undefined) : failed('saveLocal', saved.error)
}

/** Take the server's side as the local one. */
async function adoptIncoming(ops: ConflictOps): Promise<Result<void, ConflictFailure>> {
  const incoming = await ops.incoming()
  if (!incoming.ok) return failed('sync', incoming.error)

  const saved = await ops.saveLocal(incoming.value)
  return saved.ok ? ok(undefined) : failed('saveLocal', saved.error)
}

/**
 * Save the copy locally, sync it, then run what the plan has left.
 *
 * The copy is written before it is synced so a rejected upload has something to
 * undo; the rest of the plan runs inside the same rollback, because a copy left
 * behind by a later failure is the duplicate the user did not ask for.
 */
async function forkCopy(
  metadata: ConflictForkMetadata,
  remaining: ConflictEffect[],
  ops: ConflictOps,
  ctx: ConflictInterpreterContext,
): Promise<Result<void, ConflictFailure>> {
  const source = ops.local()
  const copy: SaveablePlanner = {
    ...source,
    metadata: {
      ...source.metadata,
      ...metadata,
      title: ops.sanitizeTitle(metadata.title),
      // The original keeps the publication; a copy of it starts unpublished.
      published: false,
    },
  }

  const invalid = ops.validate(copy)
  if (invalid) return failed('validate', invalid)

  const outcome = await withRollback<ConflictFailure>({
    create: async () => {
      const saved = await ops.saveLocal(copy)
      return saved.ok ? ok(undefined) : failed('saveLocal', saved.error)
    },
    rest: async () => {
      const synced = await ops.sync(copy, false)
      if (!synced.ok) return failed('sync', synced.error)
      return interpretConflictPlan(remaining, ops, ctx)
    },
    rollback: async () => {
      const deleted = await ops.deleteLocal(copy.metadata.id)
      return deleted.ok ? ok(undefined) : failed('deleteLocal', deleted.error)
    },
  })

  switch (outcome.kind) {
    case 'completed':
      return ok(undefined)
    case 'undone':
      return err(outcome.error)
    case 'undoFailed':
      // The copy survives the failure, so the caller reports the original cause
      // while the orphan stays visible in the planner list.
      console.error('Rollback of the forked planner failed:', outcome.rollbackError)
      return err(outcome.error)
    default:
      return assertNever(outcome)
  }
}

/**
 * Execute a resolution plan in order, undoing a fork that a later step defeats.
 *
 * Re-interpreting the same plan repeats the same writes under the same ids, so a
 * retried "keep both" cannot mint a second copy.
 */
export async function interpretConflictPlan(
  plan: ConflictEffect[],
  ops: ConflictOps,
  ctx: ConflictInterpreterContext,
): Promise<Result<void, ConflictFailure>> {
  const [effect, ...remaining] = plan
  if (!effect) return ok(undefined)

  switch (effect.kind) {
    case 'forkCopy':
      return forkCopy(effect.metadata, remaining, ops, ctx)
    case 'keepLocal': {
      const kept = await keepLocal(ops, ctx)
      if (!kept.ok) return kept
      break
    }
    case 'adoptIncoming': {
      const adopted = await adoptIncoming(ops)
      if (!adopted.ok) return adopted
      break
    }
    default:
      return assertNever(effect)
  }

  return interpretConflictPlan(remaining, ops, ctx)
}

/** Effects that resolve a conflict, in execution order. */
export function planConflictResolution(
  choice: ConflictResolutionChoice,
  conflict: PlannerConflict,
  ctx: ConflictResolutionContext,
): ConflictEffect[] {
  switch (choice) {
    case 'overwrite':
      return [{ kind: 'keepLocal' }]
    case 'discard':
      return [{ kind: 'adoptIncoming' }]
    case 'both':
      return [
        { kind: 'forkCopy', metadata: forkMetadata(conflict, ctx) },
        conflict.forkSide === 'local' ? { kind: 'adoptIncoming' } : { kind: 'keepLocal' },
      ]
    default:
      return assertNever(choice)
  }
}

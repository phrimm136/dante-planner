import { assertNever } from '@/lib/utils'
import { INITIAL_SYNC_VERSION } from '@/lib/constants'
import { ok, err } from '@/lib/result'
import { withRollback } from '@/lib/withRollback'

import type { Result } from '@/lib/result'
import type { AppError } from '@/lib/apiErrorClassifier'
import type {
  ConflictResolutionChoice,
  PlannerStatus,
  SaveablePlanner,
} from '../types/PlannerTypes'

export const CONFLICT_TOAST_KEY: Record<ConflictResolutionChoice, string> = {
  overwrite: 'planner:pages.plannerMD.conflict.overwriteSuccess',
  discard: 'planner:pages.plannerMD.conflict.discardSuccess',
  both: 'planner:pages.plannerMD.conflict.keepBothSuccess',
}

export type PlannerConflict = {
  forkSide: 'local' | 'incoming'
  forkTitle: string
}

export type ConflictResolutionContext = {
  now: string
  newId: () => string
  copyTitle: (title: string) => string
}

export type ConflictForkMetadata = {
  id: string
  title: string
  status: PlannerStatus
  syncVersion: number
  createdAt: string
  lastModifiedAt: string
}

export type ConflictEffect =
  | { kind: 'keepLocal' }
  | { kind: 'adoptIncoming' }
  /** `side` names which of the two versions is copied; the other keeps the id. */
  | { kind: 'forkCopy'; side: 'local' | 'incoming'; metadata: ConflictForkMetadata }

function forkMetadata(
  conflict: PlannerConflict,
  ctx: ConflictResolutionContext,
): ConflictForkMetadata {
  return {
    id: ctx.newId(),
    title: ctx.copyTitle(conflict.forkTitle),
    status: 'saved',
    syncVersion: INITIAL_SYNC_VERSION,
    createdAt: ctx.now,
    lastModifiedAt: ctx.now,
  }
}

export interface ConflictFailure {
  step: 'precondition' | 'validate' | 'saveLocal' | 'sync' | 'deleteLocal' | 'deleteRemote'
  error: AppError
}

export interface ConflictOutcome {
  id: string
  result: Result<void, ConflictFailure>
}

export interface ConflictOps {
  local: () => Promise<Result<SaveablePlanner, AppError>>
  incoming: () => Promise<Result<SaveablePlanner, AppError>>
  validate: (planner: SaveablePlanner) => AppError | null
  saveLocal: (planner: SaveablePlanner) => Promise<Result<void, AppError>>
  deleteLocal: (id: string) => Promise<Result<void, AppError>>
  deleteRemote: (id: string) => Promise<Result<void, AppError>>
  sync: (
    planner: SaveablePlanner,
    force: boolean,
  ) => Promise<Result<SaveablePlanner | null, AppError>>
  sanitizeTitle: (title: string) => string
}

export interface ConflictInterpreterContext {
  newId: () => string
  now: string
}

function failed(step: ConflictFailure['step'], error: AppError): Result<never, ConflictFailure> {
  return err({ step, error })
}

async function keepLocal(ops: ConflictOps): Promise<Result<void, ConflictFailure>> {
  const planner = await ops.local()
  if (!planner.ok) return failed('saveLocal', planner.error)

  const invalid = ops.validate(planner.value)
  if (invalid) return failed('validate', invalid)

  const synced = await ops.sync(planner.value, true)
  if (!synced.ok) return failed('sync', synced.error)

  const stored = synced.value ?? planner.value
  const saved = await ops.saveLocal({
    ...stored,
    metadata: { ...stored.metadata, status: 'saved' },
  })
  return saved.ok ? ok(undefined) : failed('saveLocal', saved.error)
}

async function adoptIncoming(ops: ConflictOps): Promise<Result<void, ConflictFailure>> {
  const incoming = await ops.incoming()
  if (!incoming.ok) return failed('sync', incoming.error)

  const saved = await ops.saveLocal(incoming.value)
  return saved.ok ? ok(undefined) : failed('saveLocal', saved.error)
}

async function forkCopy(
  effect: Extract<ConflictEffect, { kind: 'forkCopy' }>,
  remaining: ConflictEffect[],
  ops: ConflictOps,
  ctx: ConflictInterpreterContext,
): Promise<Result<void, ConflictFailure>> {
  const { metadata } = effect
  const source = effect.side === 'incoming' ? await ops.incoming() : await ops.local()
  if (!source.ok) return failed(effect.side === 'incoming' ? 'sync' : 'saveLocal', source.error)

  const copy: SaveablePlanner = {
    ...source.value,
    metadata: {
      ...source.value.metadata,
      ...metadata,
      title: ops.sanitizeTitle(metadata.title),
      published: false,
    },
  }

  const invalid = ops.validate(copy)
  if (invalid) return failed('validate', invalid)

  let uploaded = false

  const outcome = await withRollback<ConflictFailure>({
    create: async () => {
      const saved = await ops.saveLocal(copy)
      return saved.ok ? ok(undefined) : failed('saveLocal', saved.error)
    },
    rest: async () => {
      const synced = await ops.sync(copy, false)
      if (!synced.ok) return failed('sync', synced.error)
      uploaded = synced.value !== null
      return interpretConflictPlan(remaining, ops, ctx)
    },
    rollback: async () => {
      const remote = uploaded ? await ops.deleteRemote(copy.metadata.id) : ok(undefined)
      const local = await ops.deleteLocal(copy.metadata.id)

      if (!remote.ok) {
        if (!local.ok) {
          console.error('Rollback of the forked planner failed locally too:', local.error)
        }
        return failed('deleteRemote', remote.error)
      }
      return local.ok ? ok(undefined) : failed('deleteLocal', local.error)
    },
  })

  switch (outcome.kind) {
    case 'completed':
      return ok(undefined)
    case 'undone':
      return err(outcome.error)
    case 'undoFailed':
      console.error('Rollback of the forked planner failed:', outcome.rollbackError)
      return err(outcome.error)
    default:
      return assertNever(outcome)
  }
}

export async function interpretConflictPlan(
  plan: ConflictEffect[],
  ops: ConflictOps,
  ctx: ConflictInterpreterContext,
): Promise<Result<void, ConflictFailure>> {
  const [effect, ...remaining] = plan
  if (!effect) return ok(undefined)

  switch (effect.kind) {
    case 'forkCopy':
      return forkCopy(effect, remaining, ops, ctx)
    case 'keepLocal': {
      const kept = await keepLocal(ops)
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
        { kind: 'forkCopy', side: conflict.forkSide, metadata: forkMetadata(conflict, ctx) },
        conflict.forkSide === 'local' ? { kind: 'adoptIncoming' } : { kind: 'keepLocal' },
      ]
    default:
      return assertNever(choice)
  }
}

import { assertNever } from '@/lib/utils'

import type { ConflictResolutionChoice, PlannerStatus } from '../types/PlannerTypes'

/**
 * Success toast for each conflict resolution, keyed so a new choice cannot ship
 * without one.
 */
export const CONFLICT_TOAST_KEY: Record<ConflictResolutionChoice, string> = {
  overwrite: 'pages.plannerMD.conflict.overwriteSuccess',
  discard: 'pages.plannerMD.conflict.discardSuccess',
  both: 'pages.plannerMD.conflict.keepBothSuccess',
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

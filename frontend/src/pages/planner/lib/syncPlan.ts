import type { Result } from '@/lib/result'
import type { AppError } from '@/lib/apiErrorClassifier'
import type { PlannerSummary, SaveablePlanner, ServerPlannerResponse } from '../types/PlannerTypes'

/**
 * Three-way partition of a sync pass, in the order the caller executes it.
 * `pull` and `conflict` carry server summaries, `purge` carries local ones.
 */
export interface SyncPlan {
  /** Server rows to fetch and write over local: server-only, or newer than a saved local row. */
  pull: PlannerSummary[]
  /** Server rows that are newer than a local draft, so the user has to choose. */
  conflict: PlannerSummary[]
  /** Local rows whose server copy carries a tombstone. */
  purge: PlannerSummary[]
}

/** A summary's server sync version, absent meaning never synced. */
function versionOf(planner: PlannerSummary): number {
  return planner.syncVersion ?? 0
}

export type PlannerVerdict = 'pull' | 'conflict' | 'skip'

/**
 * What one server row means against its local counterpart. A row the server
 * has not advanced is left alone; an advanced row over a local draft is a
 * conflict, since pulling it would discard unsaved edits.
 */
export function categorizePlanner(
  local: PlannerSummary | undefined,
  server: PlannerSummary,
): PlannerVerdict {
  if (!local) return 'pull'
  if (versionOf(server) <= versionOf(local)) return 'skip'
  return local.status === 'draft' ? 'conflict' : 'pull'
}

/**
 * Partition a sync pass over the two summary lists it compares.
 *
 * A purge takes positive evidence: only a server tombstone (`deletedAt`) removes a local row,
 * and a local draft survives even that, since pulling the deletion would discard unsaved edits.
 * A local row the server never listed is kept — absence also describes a row that was never
 * uploaded, and deleting on that reading destroys the only copy.
 */
export function categorizeSync(server: PlannerSummary[], local: PlannerSummary[]): SyncPlan {
  const localById = new Map(local.map((p) => [p.id, p]))

  const pull: PlannerSummary[] = []
  const conflict: PlannerSummary[] = []
  const purge: PlannerSummary[] = []

  for (const serverPlanner of server) {
    const localPlanner = localById.get(serverPlanner.id)

    if (serverPlanner.deletedAt !== undefined) {
      if (localPlanner && localPlanner.status !== 'draft') purge.push(localPlanner)
      continue
    }

    switch (categorizePlanner(localPlanner, serverPlanner)) {
      case 'pull':
        pull.push(serverPlanner)
        break
      case 'conflict':
        conflict.push(serverPlanner)
        break
      case 'skip':
        break
    }
  }

  return { pull, conflict, purge }
}

/** The reads and writes a sync pass runs, injected so the phases stay testable. */
export interface SyncOps {
  /** The pulled rows, in the chunks the api client fetches them in. */
  fetchChunks: (ids: string[]) => AsyncIterable<ServerPlannerResponse[]>
  toSaveable: (response: ServerPlannerResponse) => SaveablePlanner
  saveLocal: (planner: SaveablePlanner) => Promise<Result<void, AppError>>
  deleteLocal: (id: string) => Promise<Result<void, AppError>>
  /** The failure is unread: a row this pass cannot load is one it cannot offer. */
  loadLocal: (id: string) => Promise<Result<SaveablePlanner | null, unknown>>
  fetchServer: (id: string) => Promise<Result<{ planner: SaveablePlanner }, AppError>>
}

/** The two sides of one planner the user has to choose between. */
export interface SyncConflict {
  id: string
  localPlanner: SaveablePlanner
  serverPlanner: SaveablePlanner
}

/**
 * Write every pulled row as it arrives, answering how many landed.
 *
 * Each chunk is written as it arrives, so a later chunk failing keeps what came
 * before; the response is not positionally aligned, so omitted rows stay
 * local-untouched. An empty residue asks for nothing, which is what the server
 * would reject.
 */
export async function pullServerPlanners(ids: string[], ops: SyncOps): Promise<number> {
  if (ids.length === 0) return 0

  let pulled = 0
  try {
    for await (const chunk of ops.fetchChunks(ids)) {
      for (const response of chunk) {
        try {
          const result = await ops.saveLocal(ops.toSaveable(response))
          if (result.ok) {
            pulled++
          } else {
            console.error(`Failed to save planner ${response.id}:`, result.error)
          }
        } catch (error) {
          console.error(`Failed to save planner ${response.id}:`, error)
        }
      }
    }
  } catch (error) {
    console.error('Stopped fetching planners for sync:', error)
  }
  return pulled
}

/** Drop local rows the server no longer has. The delete is idempotent. */
export async function purgeLocalPlanners(
  planners: PlannerSummary[],
  ops: SyncOps,
): Promise<number> {
  let purged = 0
  for (const local of planners) {
    try {
      await ops.deleteLocal(local.id)
      purged++
    } catch (error) {
      console.error(`Failed to purge local planner ${local.id}:`, error)
    }
  }
  return purged
}

/**
 * Read both sides of every conflicting row, dropping the ones neither side can
 * produce: a conflict the user cannot see both halves of is not one they can decide.
 */
export async function collectSyncConflicts(
  planners: PlannerSummary[],
  ops: SyncOps,
): Promise<SyncConflict[]> {
  const conflicts: SyncConflict[] = []
  for (const summary of planners) {
    try {
      const localPlanner = await ops.loadLocal(summary.id)
      const serverPlanner = await ops.fetchServer(summary.id)
      if (localPlanner.ok && localPlanner.value && serverPlanner.ok) {
        conflicts.push({
          id: summary.id,
          localPlanner: localPlanner.value,
          serverPlanner: serverPlanner.value.planner,
        })
      }
    } catch (error) {
      console.error(`Failed to load conflict planners for ${summary.id}:`, error)
    }
  }
  return conflicts
}

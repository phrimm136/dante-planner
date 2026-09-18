import type { Result } from '@/lib/result'
import type { AppError } from '@/lib/apiErrorClassifier'
import type {
  LocalTombstone,
  PlannerSummary,
  SaveablePlanner,
  ServerPlannerResponse,
} from '../types/PlannerTypes'

export interface SyncPlan {
  pull: PlannerSummary[]
  conflict: PlannerSummary[]
  purge: PlannerSummary[]
  sweep: LocalTombstone[]
  dropTombstone: LocalTombstone[]
}

function versionOf(planner: PlannerSummary): number {
  return planner.syncVersion ?? 0
}

export type PlannerVerdict = 'pull' | 'conflict' | 'skip'

export function categorizePlanner(
  local: PlannerSummary | undefined,
  server: PlannerSummary,
): PlannerVerdict {
  if (!local) return 'pull'
  if (versionOf(server) <= versionOf(local)) return 'skip'
  return local.status === 'draft' ? 'conflict' : 'pull'
}

export function categorizeSync(
  server: PlannerSummary[],
  local: PlannerSummary[],
  tombstones: LocalTombstone[],
): SyncPlan {
  const localById = new Map(local.map((p) => [p.id, p]))
  const serverById = new Map(server.map((p) => [p.id, p]))

  const sweep: LocalTombstone[] = []
  const dropTombstone: LocalTombstone[] = []
  for (const tombstone of tombstones) {
    const server = serverById.get(tombstone.id)
    const settled =
      !server || server.deletedAt !== undefined || versionOf(server) > tombstone.syncVersion
    ;(settled ? dropTombstone : sweep).push(tombstone)
  }
  const pendingDelete = new Set(sweep.map((t) => t.id))

  const pull: PlannerSummary[] = []
  const conflict: PlannerSummary[] = []
  const purge: PlannerSummary[] = []

  for (const serverPlanner of server) {
    if (pendingDelete.has(serverPlanner.id)) continue

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

  return { pull, conflict, purge, sweep, dropTombstone }
}

export interface SyncOps {
  fetchChunks: (ids: string[]) => AsyncIterable<ServerPlannerResponse[]>
  toSaveable: (response: ServerPlannerResponse) => SaveablePlanner
  saveLocal: (planner: SaveablePlanner) => Promise<Result<void, AppError>>
  deleteLocal: (id: string) => Promise<Result<void, AppError>>
  loadLocal: (id: string) => Promise<Result<SaveablePlanner | null, unknown>>
  fetchServer: (id: string) => Promise<Result<{ planner: SaveablePlanner }, AppError>>
  deleteServer: (id: string) => Promise<Result<void, AppError>>
  clearTombstone: (id: string) => Promise<Result<void, AppError>>
}

const SETTLED_DELETE_KINDS: ReadonlySet<AppError['kind']> = new Set(['notFound', 'forbidden'])

export async function settleTombstones(
  plan: Pick<SyncPlan, 'sweep' | 'dropTombstone'>,
  ops: SyncOps,
): Promise<void> {
  const swept = plan.sweep.map(async (tombstone) => {
    const result = await ops.deleteServer(tombstone.id)
    if (!result.ok && !SETTLED_DELETE_KINDS.has(result.error.kind)) {
      console.error(`Deferred deletion of ${tombstone.id} stays pending:`, result.error)
      return
    }
    await ops.clearTombstone(tombstone.id)
  })
  const dropped = plan.dropTombstone.map((tombstone) => ops.clearTombstone(tombstone.id))
  await Promise.all([...swept, ...dropped])
}

export interface SyncConflict {
  id: string
  localPlanner: SaveablePlanner
  serverPlanner: SaveablePlanner
}

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

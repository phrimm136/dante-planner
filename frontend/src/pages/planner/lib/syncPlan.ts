import type { PlannerSummary } from '../types/PlannerTypes'

/**
 * Decide whether a local planner that the server no longer has should be
 * purged from IndexedDB. Defaults to the safe choice (keep) whenever local
 * state is ambiguous or inconsistent.
 *
 * Purge only when two independent fields agree the row was previously saved
 * to the server (status='saved' AND savedAt is set). All other shapes are
 * preserved — drafts, never-synced rows, and inconsistent local state.
 */
export function shouldPurgeLocal(local: PlannerSummary): boolean {
  if (local.savedAt === null) return false
  if (local.status === 'draft') return false
  return true
}

/**
 * Three-way partition of a sync pass, in the order the caller executes it.
 * `pull` and `conflict` carry server summaries, `purge` carries local ones.
 */
export interface SyncPlan {
  /** Server rows to fetch and write over local: server-only, or newer than a saved local row. */
  pull: PlannerSummary[]
  /** Server rows that are newer than a local draft, so the user has to choose. */
  conflict: PlannerSummary[]
  /** Local rows absent from the server that carry two witnesses of a prior sync. */
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
 * `categorizePlanner` decides each server row; a local row absent from the
 * server is purged only when `shouldPurgeLocal` agrees.
 */
export function categorizeSync(server: PlannerSummary[], local: PlannerSummary[]): SyncPlan {
  const localById = new Map(local.map((p) => [p.id, p]))
  const serverIds = new Set(server.map((p) => p.id))

  const pull: PlannerSummary[] = []
  const conflict: PlannerSummary[] = []
  const purge: PlannerSummary[] = []

  for (const serverPlanner of server) {
    switch (categorizePlanner(localById.get(serverPlanner.id), serverPlanner)) {
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

  for (const localPlanner of local) {
    if (serverIds.has(localPlanner.id)) continue
    if (shouldPurgeLocal(localPlanner)) purge.push(localPlanner)
  }

  return { pull, conflict, purge }
}

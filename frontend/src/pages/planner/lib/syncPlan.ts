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

/**
 * Partition a sync pass over the two summary lists it compares.
 *
 * A server row is pulled when it has no local counterpart, or when its
 * syncVersion is above the local one and local is not a draft; the same
 * version gap over a local draft is a conflict instead, since a pull would
 * discard unsaved edits. A row at or below the local version is left alone.
 */
export function categorizeSync(server: PlannerSummary[], local: PlannerSummary[]): SyncPlan {
  const localById = new Map(local.map((p) => [p.id, p]))
  const serverIds = new Set(server.map((p) => p.id))

  const pull: PlannerSummary[] = []
  const conflict: PlannerSummary[] = []
  const purge: PlannerSummary[] = []

  for (const serverPlanner of server) {
    const localPlanner = localById.get(serverPlanner.id)
    if (!localPlanner) {
      pull.push(serverPlanner)
      continue
    }

    if (versionOf(serverPlanner) <= versionOf(localPlanner)) continue

    if (localPlanner.status === 'draft') {
      conflict.push(serverPlanner)
    } else {
      pull.push(serverPlanner)
    }
  }

  for (const localPlanner of local) {
    if (serverIds.has(localPlanner.id)) continue
    if (shouldPurgeLocal(localPlanner)) purge.push(localPlanner)
  }

  return { pull, conflict, purge }
}

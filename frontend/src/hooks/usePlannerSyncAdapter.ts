import { plannerApi } from '@/lib/plannerApi'
import { PLANNER_SCHEMA_VERSION } from '@/lib/constants'
import type {
  SaveablePlanner,
  PlannerSummary,
  ServerPlannerResponse,
  ServerPlannerSummary,
  UpsertPlannerRequest,
} from '@/types/PlannerTypes'

/**
 * Return type for usePlannerSyncAdapter hook
 * Provides server API operations only - no IndexedDB
 */
export interface PlannerSyncAdapterOperations {
  /** Sync planner to server (PUT). Uses force param for conflict override */
  syncToServer: (planner: SaveablePlanner, force?: boolean) => Promise<SaveablePlanner>
  /** Fetch planner from server by ID (GET) */
  fetchFromServer: (id: string) => Promise<SaveablePlanner | null>
  /** Delete planner from server by ID (DELETE) */
  deleteFromServer: (id: string) => Promise<void>
  /** List user's server planners */
  listFromServer: () => Promise<PlannerSummary[]>
}

/**
 * Convert server response to SaveablePlanner format
 */
function serverResponseToSaveable(response: ServerPlannerResponse): SaveablePlanner {
  let content
  try {
    content = JSON.parse(response.content)
  } catch {
    throw new Error('Failed to parse planner content from server')
  }

  return {
    metadata: {
      id: response.id,
      title: response.title,
      status: response.status,
      schemaVersion: response.schemaVersion ?? PLANNER_SCHEMA_VERSION,
      contentVersion: response.contentVersion,
      plannerType: response.plannerType,
      syncVersion: response.syncVersion,
      createdAt: response.createdAt,
      lastModifiedAt: response.lastModifiedAt,
      savedAt: response.savedAt ?? null,
      userId: String(response.userId),
      deviceId: response.deviceId ?? '',
      published: response.published,
    },
    config: {
      type: response.plannerType,
      category: response.category,
    },
    content,
  } as SaveablePlanner
}

/**
 * Convert server summary to PlannerSummary format
 */
function serverSummaryToLocal(summary: ServerPlannerSummary): PlannerSummary {
  return {
    id: summary.id,
    title: summary.title,
    plannerType: summary.plannerType,
    category: summary.category,
    status: summary.status,
    lastModifiedAt: summary.lastModifiedAt,
    savedAt: null,
    syncVersion: summary.syncVersion,
  }
}

/**
 * Adapter hook for server-only planner operations
 *
 * This adapter wraps plannerApi and provides a focused interface
 * for server synchronization. Manual save uses this adapter when
 * authenticated and sync is enabled.
 *
 * @example
 * ```tsx
 * function PlannerEditor() {
 *   const syncAdapter = usePlannerSyncAdapter()
 *
 *   const handleManualSave = async (planner: SaveablePlanner, force?: boolean) => {
 *     try {
 *       const synced = await syncAdapter.syncToServer(planner, force)
 *       console.log('Synced version:', synced.metadata.syncVersion)
 *     } catch (error) {
 *       if (error instanceof ConflictError) {
 *         // Handle conflict
 *       }
 *     }
 *   }
 * }
 * ```
 */
export function usePlannerSyncAdapter(): PlannerSyncAdapterOperations {
  /**
   * Sync planner to server using idempotent upsert
   * Creates if not exists, updates if exists
   * @param force - If true, sends force=true query param to override conflicts
   * @returns Updated planner with server-assigned fields
   */
  const syncToServer = async (
    planner: SaveablePlanner,
    force?: boolean
  ): Promise<SaveablePlanner> => {
    // Guard: Server currently only supports MD planners
    if (planner.config.type !== 'MIRROR_DUNGEON') {
      throw new Error('Server sync only supports MIRROR_DUNGEON planners')
    }

    const content = JSON.stringify(planner.content)
    const metadata = planner.metadata

    // Extract keywords from content for dedicated column storage
    const mdContent = planner.content as import('@/types/PlannerTypes').MDPlannerContent
    const selectedKeywords = mdContent.selectedKeywords ?? []

    const request: UpsertPlannerRequest = {
      id: metadata.id,
      category: planner.config.category,
      title: metadata.title,
      status: metadata.status,
      content,
      contentVersion: metadata.contentVersion,
      plannerType: metadata.plannerType,
      syncVersion: metadata.syncVersion,
      selectedKeywords,
    }

    const response = await plannerApi.upsert(metadata.id, request, force)
    return serverResponseToSaveable(response)
  }

  /**
   * Fetch planner from server by ID
   * @returns SaveablePlanner or null if not found
   */
  const fetchFromServer = async (id: string): Promise<SaveablePlanner | null> => {
    try {
      const response = await plannerApi.get(id)
      return serverResponseToSaveable(response)
    } catch (error) {
      console.error(`fetchFromServer failed for ${id}:`, error)
      return null
    }
  }

  /**
   * Delete planner from server
   */
  const deleteFromServer = async (id: string): Promise<void> => {
    return plannerApi.delete(id)
  }

  /**
   * List ALL server planners as summaries (loops through all pages)
   * @returns Array of PlannerSummary
   */
  const listFromServer = async (): Promise<PlannerSummary[]> => {
    const serverPlanners = await plannerApi.listAll()
    return serverPlanners.map(serverSummaryToLocal)
  }

  return {
    syncToServer,
    fetchFromServer,
    deleteFromServer,
    listFromServer,
  }
}

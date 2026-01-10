import { useEffect } from 'react'
import { useAuthQuery } from './useAuthQuery'
import { usePlannerStorage } from './usePlannerStorage'
import { usePlannerSync } from './usePlannerSync'
import { PLANNER_SCHEMA_VERSION } from '@/lib/constants'
import type { SaveablePlanner, PlannerSummary, ServerPlannerSummary } from '@/types/PlannerTypes'

/**
 * SSR safety check
 */
const isClient = typeof window !== 'undefined'

/**
 * Return type for usePlannerStorageAdapter hook
 */
export interface PlannerStorageAdapterOperations {
  /** Save planner (routes to server or IndexedDB based on auth) */
  savePlanner: (planner: SaveablePlanner) => Promise<SaveablePlanner>
  /** Load planner by ID */
  loadPlanner: (id: string) => Promise<SaveablePlanner | null>
  /** Delete planner by ID */
  deletePlanner: (id: string) => Promise<void>
  /** List all planners */
  listPlanners: () => Promise<PlannerSummary[]>
  /** Whether user is authenticated (uses server storage) */
  isAuthenticated: boolean
}

/**
 * Adapter hook that routes planner operations to server or IndexedDB
 *
 * For authenticated users:
 * - All operations go to the server via usePlannerSync
 * - SSE is connected for real-time multi-device sync
 *
 * For guest users:
 * - All operations use IndexedDB via usePlannerStorage
 *
 * @example
 * ```tsx
 * function PlannerEditor() {
 *   const adapter = usePlannerStorageAdapter()
 *
 *   const handleSave = async (planner: SaveablePlanner) => {
 *     const saved = await adapter.savePlanner(planner)
 *     console.log('Saved with syncVersion:', saved.metadata.syncVersion)
 *   }
 *
 *   return (
 *     <div>
 *       {adapter.isAuthenticated ? 'Syncing to cloud' : 'Saving locally'}
 *     </div>
 *   )
 * }
 * ```
 */
export function usePlannerStorageAdapter(): PlannerStorageAdapterOperations {
  const { data: user } = useAuthQuery()
  const isAuthenticated = !!user

  const localStorage = usePlannerStorage()
  const serverSync = usePlannerSync()

  // Connect/disconnect SSE based on auth
  useEffect(() => {
    if (!isClient) return

    if (isAuthenticated) {
      serverSync.connectSSE()
    } else {
      serverSync.disconnectSSE()
    }

    return () => {
      serverSync.disconnectSSE()
    }
  }, [isAuthenticated, serverSync])

  /**
   * Save planner to appropriate storage
   * Server storage includes conflict detection via syncVersion
   */
  const savePlanner = async (planner: SaveablePlanner): Promise<SaveablePlanner> => {
    if (isAuthenticated) {
      const content = JSON.stringify(planner.content)
      const metadata = planner.metadata

      // Check if this is a new planner (never saved to server)
      // A planner is "new" if syncVersion is 1 and it has no server-assigned ID pattern
      const isNewPlanner = metadata.syncVersion === 1 && !metadata.userId

      if (isNewPlanner) {
        // Create on server
        // Guard: Server currently only supports MD planners
        if (planner.config.type !== 'MIRROR_DUNGEON') {
          throw new Error('Server sync only supports MIRROR_DUNGEON planners')
        }
        const response = await serverSync.createPlanner({
          category: planner.config.category,  // Type-narrowed to MDCategory
          title: planner.content.title,
          status: metadata.status,
          content,
        })

        // Return updated planner with server response
        return {
          ...planner,
          metadata: {
            ...metadata,
            id: response.id,
            syncVersion: response.syncVersion,
            userId: String(response.userId),
            lastModifiedAt: response.lastModifiedAt,
            savedAt: response.savedAt ?? metadata.savedAt,
            published: response.published,
          },
        }
      } else {
        // Update existing planner
        const response = await serverSync.updatePlanner(metadata.id, {
          title: planner.content.title,
          status: metadata.status,
          content,
          syncVersion: metadata.syncVersion,
        })

        // Return updated planner with new syncVersion
        return {
          ...planner,
          metadata: {
            ...metadata,
            syncVersion: response.syncVersion,
            lastModifiedAt: response.lastModifiedAt,
            savedAt: response.savedAt ?? metadata.savedAt,
            published: response.published,
          },
        }
      }
    } else {
      // Guest mode - use IndexedDB
      const result = await localStorage.savePlanner(planner)
      if (!result.success) {
        // Throw to match authenticated path's error handling pattern
        const error = new Error(`Guest storage failed: ${result.errorCode}`)
        ;(error as Error & { code: string }).code = result.errorCode ?? 'saveFailed'
        throw error
      }
      return planner
    }
  }

  /**
   * Load planner by ID from appropriate storage
   */
  const loadPlanner = async (id: string): Promise<SaveablePlanner | null> => {
    if (isAuthenticated) {
      try {
        const response = await serverSync.getPlanner(id)

        // Parse content from server response
        let content
        try {
          content = JSON.parse(response.content)
        } catch {
          console.error('Failed to parse planner content from server')
          return null
        }

        // Convert server response to SaveablePlanner format
        return {
          metadata: {
            id: response.id,
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
      } catch {
        return null
      }
    } else {
      return localStorage.loadPlanner(id)
    }
  }

  /**
   * Delete planner by ID from appropriate storage
   */
  const deletePlanner = async (id: string): Promise<void> => {
    if (isAuthenticated) {
      await serverSync.deletePlanner(id)
    } else {
      await localStorage.deletePlanner(id)
    }
  }

  /**
   * List all planners from appropriate storage
   */
  const listPlanners = async (): Promise<PlannerSummary[]> => {
    if (isAuthenticated) {
      const serverPlanners = await serverSync.listPlanners()

      // Convert server format to PlannerSummary format
      // Note: Server summary API doesn't include plannerType yet, defaulting to MIRROR_DUNGEON
      return serverPlanners.map(
        (sp: ServerPlannerSummary): PlannerSummary => ({
          id: sp.id,
          title: sp.title,
          plannerType: 'MIRROR_DUNGEON', // TODO: Update when server summary includes plannerType
          category: sp.category,
          status: sp.status,
          lastModifiedAt: sp.lastModifiedAt,
          savedAt: null, // Server summary doesn't include savedAt
        })
      )
    } else {
      return localStorage.listPlanners()
    }
  }

  return {
    savePlanner,
    loadPlanner,
    deletePlanner,
    listPlanners,
    isAuthenticated,
  }
}

import { plannerApi } from '../lib/plannerApi'
import { PLANNER_SCHEMA_VERSION } from '@/lib/constants'
import { ok, err } from '@/lib/result'
import { classifyAppError } from '@/lib/apiErrorClassifier'
import { toSaveablePlanner, PlannerConfigDiscriminatedSchema } from '../schemas/PlannerSchemas'
import type { Result } from '@/lib/result'
import type { AppError } from '@/lib/apiErrorClassifier'
import type {
  SaveablePlanner,
  PlannerSummary,
  ServerAck,
  ServerPlannerResponse,
  ServerPlannerSummary,
  UpsertPlannerRequest,
} from '../types/PlannerTypes'

/**
 * A planner as the server holds it, with the version the response assigned.
 * Awaiting the response is what ties the ack to the request that produced it.
 */
export interface AcknowledgedPlanner {
  planner: SaveablePlanner
  ack: ServerAck
}

/**
 * Return type for usePlannerSyncAdapter hook
 * Provides server API operations only - no IndexedDB
 */
export interface PlannerSyncAdapterOperations {
  /** Sync planner to server (PUT). Uses force param for conflict override */
  syncToServer: (planner: SaveablePlanner, force?: boolean) => Promise<AcknowledgedPlanner>
  /** Fetch planner from server by ID (GET), reporting why the fetch failed */
  fetchFromServer: (id: string) => Promise<Result<AcknowledgedPlanner, AppError>>
  /** Delete planner from server by ID (DELETE) */
  deleteFromServer: (id: string) => Promise<void>
  /** List user's server planners */
  listFromServer: () => Promise<PlannerSummary[]>
}

/** The version a response assigned, lifted out of the full payload. */
function ackOf(response: ServerPlannerResponse): ServerAck {
  return { syncVersion: response.syncVersion }
}

/**
 * The server's copy as it must be persisted: its content under the version the
 * ack assigned. Content and version travel together, because a version names
 * the server's bytes — the sanitizer can normalize a write, and keeping local
 * bytes under the server's version would hide that divergence from a sync that
 * compares versions alone.
 */
export function acknowledgedCopy({ planner, ack }: AcknowledgedPlanner): SaveablePlanner {
  return { ...planner, metadata: { ...planner.metadata, syncVersion: ack.syncVersion } }
}

/**
 * Convert server response to SaveablePlanner format
 */
export function serverResponseToSaveable(response: ServerPlannerResponse): SaveablePlanner {
  let content
  try {
    content = JSON.parse(response.content)
  } catch {
    throw new Error('Failed to parse planner content from server')
  }

  return toSaveablePlanner(
    {
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
      deviceId: response.deviceId ?? '',
      published: response.published,
    },
    PlannerConfigDiscriminatedSchema.parse({
      type: response.plannerType,
      category: response.category,
    }),
    content,
  )
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
 *       console.log('Synced version:', synced.ack.syncVersion)
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
  // Memoize to return stable function references
  // All functions only use module-level imports, no React state/props
  return {
    syncToServer: async (
      planner: SaveablePlanner,
      force?: boolean,
    ): Promise<AcknowledgedPlanner> => {
      // Guard: Server currently only supports MD planners
      if (planner.config.type !== 'MIRROR_DUNGEON') {
        throw new Error('Server sync only supports MIRROR_DUNGEON planners')
      }

      const content = JSON.stringify(planner.content)
      const metadata = planner.metadata

      // Extract keywords from content for dedicated column storage
      const mdContent = planner.content as import('../types/PlannerTypes').MDPlannerContent
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
      return { planner: serverResponseToSaveable(response), ack: ackOf(response) }
    },

    fetchFromServer: async (id: string): Promise<Result<AcknowledgedPlanner, AppError>> => {
      try {
        const response = await plannerApi.get(id)
        return ok({ planner: serverResponseToSaveable(response), ack: ackOf(response) })
      } catch (error) {
        console.error(`fetchFromServer failed for ${id}:`, error)
        return err(classifyAppError(error))
      }
    },

    deleteFromServer: async (id: string): Promise<void> => {
      return plannerApi.delete(id)
    },

    listFromServer: async (): Promise<PlannerSummary[]> => {
      const serverPlanners = await plannerApi.listAll()
      return serverPlanners.map(serverSummaryToLocal)
    },
  } // Empty deps: functions only use module-level imports
}

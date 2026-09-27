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

export interface AcknowledgedPlanner {
  planner: SaveablePlanner
  ack: ServerAck
}

export interface PlannerSyncAdapterOperations {
  syncToServer: (planner: SaveablePlanner, force?: boolean) => Promise<AcknowledgedPlanner>
  fetchFromServer: (id: string) => Promise<Result<AcknowledgedPlanner, AppError>>
  deleteFromServer: (id: string) => Promise<Result<void, AppError>>
  listFromServer: () => Promise<PlannerSummary[]>
}

function ackOf(response: ServerPlannerResponse): ServerAck {
  return { syncVersion: response.syncVersion }
}

export function acknowledgedCopy({ planner, ack }: AcknowledgedPlanner): SaveablePlanner {
  return { ...planner, metadata: { ...planner.metadata, syncVersion: ack.syncVersion } }
}

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
      published: response.published,
    },
    PlannerConfigDiscriminatedSchema.parse({
      type: response.plannerType,
      category: response.category,
    }),
    content,
  )
}

function serverSummaryToLocal(summary: ServerPlannerSummary): PlannerSummary {
  return {
    id: summary.id,
    title: summary.title,
    plannerType: summary.plannerType,
    category: summary.category,
    status: summary.status,
    lastModifiedAt: summary.lastModifiedAt,
    syncVersion: summary.syncVersion,
    ...(summary.deletedAt !== undefined && { deletedAt: summary.deletedAt }),
  }
}

export function toUpsertRequest(planner: SaveablePlanner): UpsertPlannerRequest {
  if (planner.config.type !== 'MIRROR_DUNGEON') {
    throw new Error('Server sync only supports MIRROR_DUNGEON planners')
  }

  const metadata = planner.metadata

  const mdContent = planner.content as import('../types/PlannerTypes').MDPlannerContent
  const selectedKeywords = mdContent.selectedKeywords ?? []

  return {
    id: metadata.id,
    category: planner.config.category,
    title: metadata.title,
    status: metadata.status,
    content: JSON.stringify(planner.content),
    contentVersion: metadata.contentVersion,
    plannerType: metadata.plannerType,
    syncVersion: metadata.syncVersion,
    selectedKeywords,
  }
}

export function usePlannerSyncAdapter(): PlannerSyncAdapterOperations {
  return {
    syncToServer: async (
      planner: SaveablePlanner,
      force?: boolean,
    ): Promise<AcknowledgedPlanner> => {
      const response = await plannerApi.upsert(planner.metadata.id, toUpsertRequest(planner), force)
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

    deleteFromServer: async (id: string): Promise<Result<void, AppError>> => {
      try {
        await plannerApi.delete(id)
        return ok(undefined)
      } catch (error) {
        return err(classifyAppError(error))
      }
    },

    listFromServer: async (): Promise<PlannerSummary[]> => {
      const serverPlanners = await plannerApi.listAll()
      return serverPlanners.map(serverSummaryToLocal)
    },
  }
}

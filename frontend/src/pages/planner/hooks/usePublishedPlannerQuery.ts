import { useSuspenseQuery } from '@tanstack/react-query'

import { NotFoundError } from '@/lib/apiErrors'
import { validateData } from '@/lib/validation'
import { fetchPublishedPlannerRaw } from '../lib/fetchPublishedPlannerRaw'
import { publishedPlannerQueryKeys } from '../lib/publishedPlannerQueryKeys'
import { PublishedPlannerDetailSchema } from '../schemas/PlannerListSchemas'
import { validateSaveablePlanner } from '../schemas/PlannerSchemas'

import type { PublishedPlannerDetail } from '../types/PlannerListTypes'
import type { SaveablePlanner } from '../types/PlannerTypes'
import { GC_TIME, STALE_TIME } from '@/lib/constants'

export interface PublishedPlannerQueryResult {
  apiData: PublishedPlannerDetail
  planner: SaveablePlanner
}

export interface PublishedPlannerRemoved {
  removed: true
}

export type PublishedPlannerQueryState = PublishedPlannerQueryResult | PublishedPlannerRemoved

export function isPlannerRemoved(
  state: PublishedPlannerQueryState,
): state is PublishedPlannerRemoved {
  return 'removed' in state
}

/**
 * A removed verdict never keeps its freshness: unpublishing is reversible, and
 * a cached one would otherwise outlive the republish on every other device,
 * which has no event left to tell it otherwise.
 *
 * `ensureSuspenseTimers` raises any suspense query's staleTime to a 1000ms
 * floor, so the zero only lands in full on the route loader's `fetchQuery` —
 * which is what a navigation runs, and therefore where the re-ask is
 * guaranteed rather than merely likely.
 */
export function publishedPlannerStaleTime(data: PublishedPlannerQueryState | undefined): number {
  return data !== undefined && isPlannerRemoved(data) ? 0 : STALE_TIME.MEDIUM
}

export { publishedPlannerQueryKeys }

export async function fetchPublishedPlanner(
  plannerId: string,
  signal?: AbortSignal,
): Promise<PublishedPlannerQueryState> {
  return parsePublishedPlanner(plannerId, fetchPublishedPlannerRaw(plannerId, signal))
}

export async function parsePublishedPlanner(
  plannerId: string,
  raw: Promise<unknown>,
): Promise<PublishedPlannerQueryState> {
  let data: unknown
  try {
    data = await raw
  } catch (error) {
    if (error instanceof NotFoundError) return { removed: true }
    throw error
  }
  const apiData = validateData(
    data,
    PublishedPlannerDetailSchema,
    `planner published / ${plannerId}`,
  )

  // The content arrives as a JSON string a publisher wrote, so it is ingest like
  // any other: parsed, then gated in draft mode, which tolerates the partial
  // shapes a published draft can legitimately carry.
  let contentData: unknown
  try {
    contentData = JSON.parse(apiData.content)
  } catch {
    throw new Error(`planner published / ${plannerId}: content is not JSON`)
  }

  const planner = validateSaveablePlanner(
    {
      metadata: {
        id: apiData.id,
        title: apiData.title,
        status: apiData.status,
        schemaVersion: apiData.schemaVersion,
        contentVersion: apiData.contentVersion,
        plannerType: apiData.plannerType,
        syncVersion: apiData.syncVersion,
        createdAt: apiData.createdAt,
        lastModifiedAt: apiData.lastModifiedAt,
        published: true,
      },
      config: { type: apiData.plannerType, category: apiData.category },
      content: contentData,
    },
    'draft',
  )

  return { apiData, planner }
}

export function usePublishedPlannerQuery(plannerId: string): PublishedPlannerQueryState {
  const query = useSuspenseQuery({
    queryKey: publishedPlannerQueryKeys.detail(plannerId),
    queryFn: ({ signal }) => fetchPublishedPlanner(plannerId, signal),
    staleTime: (query) => publishedPlannerStaleTime(query.state.data),
    gcTime: GC_TIME.MEDIUM,
  })

  return query.data
}

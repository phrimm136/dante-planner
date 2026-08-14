/**
 * Published Planner Query Hook
 *
 * Fetches a single published planner by ID using Suspense.
 * Returns both raw API data (for header/footer) and parsed SaveablePlanner (for viewer).
 *
 * Pattern: useSavedPlannerQuery.ts (useSuspenseQuery + query key factory)
 */

import { useSuspenseQuery } from '@tanstack/react-query'

import { ApiClient, NotFoundError } from '@/lib/api'
import { validateData } from '@/lib/validation'
import { PublishedPlannerDetailSchema } from '../schemas/PlannerListSchemas'

import type { PublishedPlannerDetail, MDCategory, RRCategory } from '../types/PlannerListTypes'
import type { SaveablePlanner, MDPlannerContent, RRPlannerContent } from '../types/PlannerTypes'
import { STALE_TIME, GC_TIME } from '@/lib/constants'

/**
 * Return type for usePublishedPlannerQuery
 * Contains both raw API data and parsed planner for different consumers
 */
export interface PublishedPlannerQueryResult {
  /** Raw API response with user state (votes, subscription, report) - for header/footer */
  apiData: PublishedPlannerDetail
  /** Parsed SaveablePlanner structure - for PlannerViewer */
  planner: SaveablePlanner
}

/** The planner is no longer published — deleted or unpublished elsewhere. */
export interface PublishedPlannerRemoved {
  removed: true
}

export type PublishedPlannerQueryState = PublishedPlannerQueryResult | PublishedPlannerRemoved

/** Narrows the query state to the removed case. */
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

// ============================================================================
// Query Keys
// ============================================================================

/**
 * Query key factory for published planner queries
 */
export const publishedPlannerQueryKeys = {
  /** Key for single published planner detail */
  detail: (id: string) => ['publishedPlanner', id] as const,
}

// ============================================================================
// Query Function (exported for use in route loaders)
// ============================================================================

/**
 * Fetches a published planner by ID and parses it into the query result shape.
 * Exported so route loaders can prefetch into the TanStack Query cache,
 * preventing a duplicate network request when the component mounts.
 */
export async function fetchPublishedPlanner(
  plannerId: string,
  signal?: AbortSignal,
): Promise<PublishedPlannerQueryState> {
  let data: unknown
  try {
    data = await ApiClient.get(`/api/planner/md/published/${plannerId}`, { signal })
  } catch (error) {
    // An entry opened from a stale list can have been deleted on another
    // device; that is an answer to show, not an error to escalate.
    if (error instanceof NotFoundError) return { removed: true }
    throw error
  }
  const apiData = validateData(
    data,
    PublishedPlannerDetailSchema,
    `planner published / ${plannerId}`,
  )

  // Parse content JSON and construct SaveablePlanner
  // Server is trusted source - no frontend validation needed
  const contentData = JSON.parse(apiData.content)
  const metadata = {
    id: apiData.id,
    title: apiData.title,
    status: apiData.status,
    schemaVersion: apiData.schemaVersion,
    contentVersion: apiData.contentVersion,
    plannerType: apiData.plannerType,
    syncVersion: apiData.syncVersion,
    createdAt: apiData.createdAt,
    lastModifiedAt: apiData.lastModifiedAt,
    savedAt: apiData.createdAt,
    userId: null,
    deviceId: 'published',
    published: true,
  }

  // Type narrowing based on plannerType
  const planner: SaveablePlanner =
    apiData.plannerType === 'MIRROR_DUNGEON'
      ? {
          metadata,
          config: { type: 'MIRROR_DUNGEON', category: apiData.category as MDCategory },
          content: contentData as MDPlannerContent,
        }
      : {
          metadata,
          config: { type: 'REFRACTED_RAILWAY', category: apiData.category as RRCategory },
          content: contentData as RRPlannerContent,
        }

  return { apiData, planner }
}

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook to load a published planner by ID using Suspense
 *
 * @param plannerId - The planner ID to load
 * @returns Object containing apiData (for header/footer) and planner (for viewer)
 *
 * @example
 * ```tsx
 * function PlannerDetailPage() {
 *   const { id } = useParams()
 *   const { apiData, planner } = usePublishedPlannerQuery(id)
 *
 *   return (
 *     <>
 *       <PublishedPlannerHeader planner={apiData} />
 *       <PlannerViewer planner={planner} />
 *       <PlannerDetailFooter planner={apiData} />
 *     </>
 *   )
 * }
 * ```
 */
export function usePublishedPlannerQuery(plannerId: string): PublishedPlannerQueryState {
  const query = useSuspenseQuery({
    queryKey: publishedPlannerQueryKeys.detail(plannerId),
    queryFn: ({ signal }) => fetchPublishedPlanner(plannerId, signal),
    staleTime: (query) => publishedPlannerStaleTime(query.state.data),
    gcTime: GC_TIME.MEDIUM,
  })

  return query.data
}

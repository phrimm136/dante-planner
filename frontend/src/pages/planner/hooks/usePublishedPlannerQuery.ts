/**
 * Published Planner Query Hook
 *
 * Fetches a single published planner by ID using Suspense.
 * Returns both raw API data (for header/footer) and parsed SaveablePlanner (for viewer).
 *
 * Pattern: useSavedPlannerQuery.ts (useSuspenseQuery + query key factory)
 */

import { useSuspenseQuery } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
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
): Promise<PublishedPlannerQueryResult> {
  const data = await ApiClient.get(`/api/planner/md/published/${plannerId}`)
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
export function usePublishedPlannerQuery(plannerId: string): PublishedPlannerQueryResult {
  const query = useSuspenseQuery({
    queryKey: publishedPlannerQueryKeys.detail(plannerId),
    queryFn: () => fetchPublishedPlanner(plannerId),
    staleTime: STALE_TIME.MEDIUM,
    gcTime: GC_TIME.MEDIUM,
  })

  return query.data
}

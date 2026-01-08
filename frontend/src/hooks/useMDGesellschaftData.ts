/**
 * MD Gesellschaft Data Hook
 *
 * Fetches community planner list data for /planner/md/gesellschaft route.
 * - mode='published': All published planners from API
 * - mode='best': Recommended/featured planners from API
 *
 * Pattern: useSuspenseQuery + Zod validation
 */

import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { PLANNER_LIST } from '@/lib/constants'
import { PaginatedPlannersSchema } from '@/schemas/PlannerListSchemas'

import type { MDCategory } from '@/lib/constants'
import type { MDGesellschaftMode } from '@/types/MDPlannerListTypes'

// ============================================================================
// Query Keys
// ============================================================================

/**
 * Query key factory for gesellschaft (community) planner queries
 * Renamed from plannerListQueryKeys for clarity
 */
export const gesellschaftQueryKeys = {
  /** Base key for all gesellschaft planner queries */
  all: ['gesellschaft'] as const,

  /** Key for published planners */
  published: (params: {
    page: number
    size: number
    category?: MDCategory
    search?: string
  }) => [...gesellschaftQueryKeys.all, 'published', params] as const,

  /** Key for recommended planners (best) */
  recommended: (params: {
    page: number
    size: number
    category?: MDCategory
    search?: string
  }) => [...gesellschaftQueryKeys.all, 'recommended', params] as const,
}

// ============================================================================
// Query Options
// ============================================================================

/**
 * Query options for published planners
 */
function createPublishedPlannersQueryOptions(params: {
  page: number
  size: number
  category?: MDCategory
  search?: string
}) {
  return queryOptions({
    queryKey: gesellschaftQueryKeys.published(params),
    queryFn: async () => {
      const searchParams = new URLSearchParams()
      searchParams.append('page', String(params.page))
      searchParams.append('size', String(params.size))
      if (params.category) searchParams.append('category', params.category)
      if (params.search) searchParams.append('q', params.search)

      const data = await ApiClient.get(`/api/planner/md/published?${searchParams.toString()}`)
      const result = PaginatedPlannersSchema.safeParse(data)

      if (!result.success) {
        throw new Error(`[gesellschaft published] Validation failed: ${result.error.message}`)
      }

      return result.data
    },
    staleTime: 60 * 1000, // 1 minute - list data changes frequently
  })
}

/**
 * Query options for recommended planners
 */
function createRecommendedPlannersQueryOptions(params: {
  page: number
  size: number
  category?: MDCategory
  search?: string
}) {
  return queryOptions({
    queryKey: gesellschaftQueryKeys.recommended(params),
    queryFn: async () => {
      const searchParams = new URLSearchParams()
      searchParams.append('page', String(params.page))
      searchParams.append('size', String(params.size))
      if (params.category) searchParams.append('category', params.category)
      if (params.search) searchParams.append('q', params.search)

      const data = await ApiClient.get(`/api/planner/md/recommended?${searchParams.toString()}`)
      const result = PaginatedPlannersSchema.safeParse(data)

      if (!result.success) {
        throw new Error(`[gesellschaft recommended] Validation failed: ${result.error.message}`)
      }

      return result.data
    },
    staleTime: 5 * 60 * 1000, // 5 minutes - recommended list is more stable
  })
}

// ============================================================================
// Hook Options Interface
// ============================================================================

export interface UseMDGesellschaftDataOptions {
  /** Display mode: 'published' (all) or 'best' (recommended only) */
  mode: MDGesellschaftMode
  /** Current page number (0-indexed) */
  page: number
  /** MD category filter */
  category?: MDCategory
  /** Search query string */
  search?: string
}

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook that fetches community planner list data
 * Suspends while loading - wrap in Suspense boundary
 *
 * @param options - Mode, pagination, and category options
 * @returns Paginated planner list data
 *
 * @example
 * ```tsx
 * function GesellschaftList() {
 *   const { data } = useMDGesellschaftData({
 *     mode: 'published',
 *     page: 0,
 *     category: '5F',
 *   });
 *
 *   return <PlannerGrid planners={data.content} />;
 * }
 * ```
 */
export function useMDGesellschaftData(options: UseMDGesellschaftDataOptions) {
  const { mode, page, category, search } = options

  if (mode === 'best') {
    return useSuspenseQuery(
      createRecommendedPlannersQueryOptions({
        page,
        size: PLANNER_LIST.PAGE_SIZE,
        category,
        search,
      })
    )
  }

  return useSuspenseQuery(
    createPublishedPlannersQueryOptions({
      page,
      size: PLANNER_LIST.PAGE_SIZE,
      category,
      search,
    })
  )
}

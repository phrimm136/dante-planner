/**
 * Planner List Data Hook
 *
 * Fetches planner list data with conditional source:
 * - Community view: Always from server API
 * - My Plans view: IndexedDB for guests, server for authenticated users
 *
 * Pattern: useIdentityListData.ts (useSuspenseQuery + Zod validation)
 */

import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { PLANNER_LIST } from '@/lib/constants'
import { PaginatedPlannersSchema } from '@/schemas/PlannerListSchemas'

import type { MDCategory, PlannerSortOption } from '@/types/PlannerListTypes'

// ============================================================================
// Query Keys
// ============================================================================

/**
 * Query key factory for planner list queries
 */
export const plannerListQueryKeys = {
  /** Base key for all planner list queries */
  all: ['plannerList'] as const,

  /** Key for published planners (community view) */
  published: (params: {
    page: number
    size: number
    category?: MDCategory
    sort?: PlannerSortOption
    search?: string
  }) => [...plannerListQueryKeys.all, 'published', params] as const,

  /** Key for recommended planners (featured view) */
  recommended: (params: {
    page: number
    size: number
    category?: MDCategory
    search?: string
  }) => [...plannerListQueryKeys.all, 'recommended', params] as const,
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
  sort?: PlannerSortOption
  search?: string
}) {
  return queryOptions({
    queryKey: plannerListQueryKeys.published(params),
    queryFn: async () => {
      const searchParams = new URLSearchParams()
      searchParams.append('page', String(params.page))
      searchParams.append('size', String(params.size))
      if (params.category) searchParams.append('category', params.category)
      if (params.sort) searchParams.append('sort', params.sort)
      if (params.search) searchParams.append('q', params.search)

      const data = await ApiClient.get(`/api/planner/md/published?${searchParams.toString()}`)
      const result = PaginatedPlannersSchema.safeParse(data)

      if (!result.success) {
        throw new Error(`[planner list] Validation failed: ${result.error.message}`)
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
    queryKey: plannerListQueryKeys.recommended(params),
    queryFn: async () => {
      const searchParams = new URLSearchParams()
      searchParams.append('page', String(params.page))
      searchParams.append('size', String(params.size))
      if (params.category) searchParams.append('category', params.category)
      if (params.search) searchParams.append('q', params.search)

      const data = await ApiClient.get(`/api/planner/md/recommended?${searchParams.toString()}`)
      const result = PaginatedPlannersSchema.safeParse(data)

      if (!result.success) {
        throw new Error(`[planner recommended] Validation failed: ${result.error.message}`)
      }

      return result.data
    },
    staleTime: 5 * 60 * 1000, // 5 minutes - recommended list is more stable
  })
}

// ============================================================================
// Hook Options Interface
// ============================================================================

export interface UsePlannerListDataOptions {
  /** Filter mode: 'all' or 'recommended' */
  filter: 'all' | 'recommended'
  /** Current page number (0-indexed) */
  page: number
  /** MD category filter */
  category?: MDCategory
  /** Sort option (only for 'all' filter) */
  sort?: PlannerSortOption
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
 * @param options - Filter, pagination, and sort options
 * @returns Paginated planner list data
 *
 * @example
 * ```tsx
 * function CommunityList() {
 *   const { data } = usePlannerListData({
 *     filter: 'all',
 *     page: 0,
 *     category: '5F',
 *     sort: 'recent',
 *   });
 *
 *   return <PlannerGrid planners={data.content} />;
 * }
 * ```
 */
export function usePlannerListData(options: UsePlannerListDataOptions) {
  const { filter, page, category, sort, search } = options

  if (filter === 'recommended') {
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
      sort,
      search,
    })
  )
}

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
import { PLANNER_LIST, STALE_TIME } from '@/lib/constants'
import { validateData } from '@/lib/validation'
import { PaginatedPlannersSchema } from '../schemas/PlannerListSchemas'

import type { MDCategory } from '@/shared/gameData'
import type { MDGesellschaftMode } from '../types/MDPlannerListTypes'

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
    keyword?: string
    identity?: string
    ego?: string
    gift?: string
    themePack?: string
  }) => [...gesellschaftQueryKeys.all, 'published', params] as const,

  /** Key for recommended planners (best) */
  recommended: (params: {
    page: number
    size: number
    category?: MDCategory
    search?: string
    keyword?: string
    identity?: string
    ego?: string
    gift?: string
    themePack?: string
  }) => [...gesellschaftQueryKeys.all, 'recommended', params] as const,
}

// ============================================================================
// Query Options
// ============================================================================

interface GesellschaftQueryParams {
  page: number
  size: number
  category?: MDCategory
  search?: string
  keyword?: string
  identity?: string
  ego?: string
  gift?: string
  themePack?: string
}

/**
 * Query options for the planner list of a given mode
 */
function createGesellschaftQueryOptions(mode: MDGesellschaftMode, params: GesellschaftQueryParams) {
  const isBest = mode === 'best'

  return queryOptions({
    queryKey: isBest
      ? gesellschaftQueryKeys.recommended(params)
      : gesellschaftQueryKeys.published(params),
    queryFn: async ({ signal }) => {
      const searchParams = new URLSearchParams()
      searchParams.append('page', String(params.page))
      searchParams.append('size', String(params.size))
      if (params.category) searchParams.append('category', params.category)
      if (params.search) searchParams.append('q', params.search)
      if (params.keyword) searchParams.append('keyword', params.keyword)
      if (params.identity) searchParams.append('identity', params.identity)
      if (params.ego) searchParams.append('ego', params.ego)
      if (params.gift) searchParams.append('gift', params.gift)
      if (params.themePack) searchParams.append('themePack', params.themePack)

      const path = isBest ? '/api/planner/md/recommended' : '/api/planner/md/published'
      const data = await ApiClient.get(`${path}?${searchParams.toString()}`, { signal })
      return validateData(
        data,
        PaginatedPlannersSchema,
        isBest ? 'gesellschaft recommended' : 'gesellschaft published',
      )
    },
    staleTime: isBest ? STALE_TIME.MEDIUM : STALE_TIME.SHORT,
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
  /** Comma-separated keyword filter */
  keyword?: string
  /** Comma-separated identity ID filter */
  identity?: string
  /** Comma-separated EGO ID filter */
  ego?: string
  /** Comma-separated gift ID filter */
  gift?: string
  /** Comma-separated theme pack ID filter */
  themePack?: string
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
  const { mode, page, category, search, keyword, identity, ego, gift, themePack } = options

  return useSuspenseQuery(
    createGesellschaftQueryOptions(mode, {
      page,
      size: PLANNER_LIST.PAGE_SIZE,
      category,
      search,
      keyword,
      identity,
      ego,
      gift,
      themePack,
    }),
  )
}

/**
 * MD User Planners Data Hook
 *
 * Fetches personal planners for the /planner/md route.
 * - Guests: IndexedDB only
 * - Authenticated: Server API (with IndexedDB merge handled by adapter)
 *
 * Pattern: usePlannerStorageAdapter wrapping + TanStack Query
 */

import { useMemo } from 'react'
import { useSuspenseQuery, queryOptions, useQueryClient } from '@tanstack/react-query'

import { usePlannerStorageAdapter } from './usePlannerStorageAdapter'
import { useAuthQuery } from './useAuthQuery'

import type { PlannerSummary } from '@/types/PlannerTypes'
import type { MDCategory } from '@/lib/constants'

// ============================================================================
// Query Keys
// ============================================================================

/**
 * Query key factory for user planner queries
 * Includes auth state to invalidate cache on login/logout
 */
export const userPlannersQueryKeys = {
  /** Base key for all user planner queries */
  all: ['userPlanners'] as const,

  /** Key for user's planner list (includes auth state for cache separation) */
  list: (isAuthenticated: boolean) =>
    [...userPlannersQueryKeys.all, 'list', { isAuthenticated }] as const,
}

// ============================================================================
// Hook Options Interface
// ============================================================================

export interface UseMDUserPlannersDataOptions {
  /** MD category filter (optional) */
  category?: MDCategory
  /** Current page number (0-indexed) */
  page: number
  /** Search query for title filtering (optional) */
  search?: string
}

// ============================================================================
// Hook Return Type
// ============================================================================

export interface MDUserPlannersResult {
  /** Filtered planners for current page */
  planners: PlannerSummary[]
  /** Total number of planners (after category filter) */
  totalCount: number
  /** Whether user is authenticated */
  isAuthenticated: boolean
}

// ============================================================================
// Constants
// ============================================================================

const PAGE_SIZE = 20

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook that fetches user's personal planners
 * Suspends while loading - wrap in Suspense boundary
 *
 * Data source is automatically selected based on authentication:
 * - Guest: IndexedDB via usePlannerStorage
 * - Authenticated: Server API via usePlannerSync
 *
 * Note: Client-side filtering and pagination (not server-side for local data)
 *
 * @param options - Category filter and pagination options
 * @returns Personal planners with metadata
 *
 * @example
 * ```tsx
 * function MyPlansList() {
 *   const { planners, totalCount, isAuthenticated } = useMDUserPlannersData({
 *     category: '5F',
 *     page: 0,
 *   });
 *
 *   return (
 *     <div>
 *       {isAuthenticated ? 'Cloud synced' : 'Local only'}
 *       <PlannerGrid planners={planners} />
 *     </div>
 *   );
 * }
 * ```
 */
export function useMDUserPlannersData(
  options: UseMDUserPlannersDataOptions
): MDUserPlannersResult {
  const { category, page, search } = options
  const adapter = usePlannerStorageAdapter()
  const { data: user } = useAuthQuery()
  const isAuthenticated = !!user

  // Fetch all planners via adapter (handles auth routing internally)
  const { data: allPlanners } = useSuspenseQuery(
    queryOptions({
      queryKey: userPlannersQueryKeys.list(isAuthenticated),
      queryFn: async () => {
        return adapter.listPlanners()
      },
      staleTime: 30 * 1000, // 30 seconds - user data can change frequently
    })
  )

  // Memoize filtering to prevent recalculation on unrelated re-renders
  const { paginatedPlanners, totalCount } = useMemo(() => {
    const normalizedSearch = search?.toLowerCase().trim()

    // Client-side filtering by category and search
    const filtered = allPlanners.filter((p) => {
      if (category && p.category !== category) return false
      if (normalizedSearch && !p.title.toLowerCase().includes(normalizedSearch)) return false
      return true
    })

    // Client-side pagination
    const startIndex = page * PAGE_SIZE
    return {
      paginatedPlanners: filtered.slice(startIndex, startIndex + PAGE_SIZE),
      totalCount: filtered.length,
    }
  }, [allPlanners, category, search, page])

  return {
    planners: paginatedPlanners,
    totalCount,
    isAuthenticated,
  }
}

// ============================================================================
// Cache Invalidation Helper
// ============================================================================

/**
 * Hook to get invalidation function for user planners cache
 * Use after planner create/update/delete operations
 *
 * @example
 * ```tsx
 * const invalidate = useInvalidateUserPlanners();
 * await adapter.deletePlanner(id);
 * invalidate();
 * ```
 */
export function useInvalidateUserPlanners() {
  const queryClient = useQueryClient()

  return () => {
    void queryClient.invalidateQueries({
      queryKey: userPlannersQueryKeys.all,
    })
  }
}

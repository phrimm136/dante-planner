/**
 * Hidden Planners Query Hook
 *
 * Fetches list of planners hidden from recommended by moderator/admin.
 * Requires ROLE_MODERATOR or ROLE_ADMIN authorization.
 *
 * Pattern: useNotificationsQuery.ts (pagination pattern)
 */

import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { z } from 'zod'

import { ApiClient } from '@/lib/api'

// ============================================================================
// Types & Schema
// ============================================================================

const HiddenPlannerSchema = z.object({
  id: z.string().uuid(),
  title: z.string(),
  category: z.string(),
  authorUsernameEpithet: z.string(),
  authorUsernameSuffix: z.string(),
  hiddenReason: z.string(),
  hiddenByModeratorUsername: z.string(),
  hiddenAt: z.string(),
})

const HiddenPlannerPageSchema = z.object({
  content: z.array(HiddenPlannerSchema),
  totalPages: z.number(),
  totalElements: z.number(),
  currentPage: z.number(),
})

export type HiddenPlanner = z.infer<typeof HiddenPlannerSchema>
export type HiddenPlannerPage = z.infer<typeof HiddenPlannerPageSchema>

// ============================================================================
// Query Key Factory
// ============================================================================

export const hiddenPlannersQueryKeys = {
  all: ['moderator', 'hiddenPlanners'] as const,
  page: (page: number, size: number) =>
    ['moderator', 'hiddenPlanners', page, size] as const,
}

// ============================================================================
// Query Options
// ============================================================================

function createHiddenPlannersQueryOptions(page: number = 0, size: number = 20) {
  return queryOptions({
    queryKey: hiddenPlannersQueryKeys.page(page, size),
    queryFn: async (): Promise<HiddenPlannerPage> => {
      const data = await ApiClient.get(
        `/api/admin/planner/hidden?page=${page}&size=${size}`
      )
      const result = HiddenPlannerPageSchema.safeParse(data)

      if (!result.success) {
        console.error('Hidden planners response validation failed:', result.error)
        throw new Error('Invalid hidden planners response from server')
      }

      return result.data
    },
    staleTime: 60 * 1000,
  })
}

// ============================================================================
// Hook
// ============================================================================

/**
 * Hook for fetching hidden planners (moderator/admin only)
 *
 * Uses useSuspenseQuery - wrap in Suspense boundary for loading states.
 *
 * @param page - Page number (0-indexed)
 * @param size - Page size (default: 20)
 * @returns Query result with hidden planners data
 *
 * @example
 * ```tsx
 * function HiddenPlannerList() {
 *   const { data } = useHiddenPlannersQuery(0, 20);
 *
 *   return (
 *     <div>
 *       {data.content.map(planner => (
 *         <HiddenPlannerCard key={planner.id} planner={planner} />
 *       ))}
 *     </div>
 *   );
 * }
 *
 * // Wrap in Suspense
 * <Suspense fallback={<LoadingSpinner />}>
 *   <HiddenPlannerList />
 * </Suspense>
 * ```
 */
export function useHiddenPlannersQuery(page: number = 0, size: number = 20) {
  return useSuspenseQuery(createHiddenPlannersQueryOptions(page, size))
}

/**
 * Planner Bookmark Mutation Hook
 *
 * Handles bookmarking/unbookmarking community planners.
 * Toggle endpoint - calling again removes the bookmark.
 * Invalidates planner list cache on success.
 *
 * Pattern: usePlannerVote.ts (mutation + cache invalidation)
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { BookmarkResponseSchema } from '@/schemas/PlannerListSchemas'
import { plannerListQueryKeys } from './usePlannerListData'

import type { BookmarkResponse } from '@/types/PlannerListTypes'

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook for bookmarking community planners
 *
 * @example
 * ```tsx
 * function PlannerCard({ planner }) {
 *   const bookmark = usePlannerBookmark();
 *
 *   const handleBookmark = () => {
 *     bookmark.mutate(planner.id);
 *   };
 *
 *   return (
 *     <button
 *       onClick={handleBookmark}
 *       disabled={bookmark.isPending}
 *       aria-pressed={planner.isBookmarked}
 *     >
 *       {planner.isBookmarked ? 'Bookmarked' : 'Bookmark'}
 *     </button>
 *   );
 * }
 * ```
 */
export function usePlannerBookmark() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (plannerId: string): Promise<BookmarkResponse> => {
      const data = await ApiClient.post(`/api/planner/md/${plannerId}/bookmark`)
      const result = BookmarkResponseSchema.safeParse(data)

      if (!result.success) {
        console.error('Bookmark response validation failed:', result.error)
        throw new Error('Invalid bookmark response from server')
      }

      return result.data
    },
    onSuccess: () => {
      // Invalidate all planner list queries to refresh bookmark state
      void queryClient.invalidateQueries({ queryKey: plannerListQueryKeys.all })
    },
    onError: (error) => {
      console.error('Bookmark failed:', error)
    },
  })
}

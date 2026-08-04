/**
 * Planner Bookmark Mutation Hook
 *
 * Handles bookmarking/unbookmarking community planners.
 * Names the desired state rather than asking for a toggle, so a retried request
 * cannot un-bookmark. Invalidates planner list cache on success.
 *
 * Pattern: usePlannerVote.ts (mutation + cache invalidation)
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { validateData } from '@/lib/validation'
import { BookmarkResponseSchema } from '../schemas/PlannerListSchemas'
import { gesellschaftQueryKeys } from './useMDGesellschaftData'

import type { BookmarkResponse } from '../types/PlannerListTypes'

// ============================================================================
// Main Hook
// ============================================================================

/** The planner whose bookmark is being driven, and the state it is driven to. */
export interface BookmarkVariables {
  plannerId: string
  bookmarked: boolean
}

/**
 * Hook for bookmarking community planners
 *
 * @example
 * ```tsx
 * function PlannerCard({ planner }) {
 *   const bookmark = usePlannerBookmark();
 *
 *   const handleBookmark = () => {
 *     bookmark.mutate({ plannerId: planner.id, bookmarked: !planner.isBookmarked });
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
    mutationFn: async ({ plannerId, bookmarked }: BookmarkVariables): Promise<BookmarkResponse> => {
      const data = await ApiClient.post(`/api/planner/md/${plannerId}/bookmark`, { bookmarked })
      return validateData(data, BookmarkResponseSchema, 'planner bookmark')
    },
    onSuccess: () => {
      // Invalidate all planner list queries to refresh bookmark state
      void queryClient.invalidateQueries({ queryKey: gesellschaftQueryKeys.all })
    },
    onError: (error) => {
      console.error('Bookmark failed:', error)
    },
  })
}

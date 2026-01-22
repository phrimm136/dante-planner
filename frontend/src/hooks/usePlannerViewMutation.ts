/**
 * Planner View Recording Mutation Hook
 *
 * Records a view for a published planner and receives updated view count.
 * Backend handles daily deduplication per viewer (UTC date-based).
 *
 * Pattern: usePlannerVote (mutation with cache invalidation on success)
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiClient } from '@/lib/api'
import { publishedPlannerQueryKeys } from './usePublishedPlannerQuery'

interface ViewCountResponse {
  viewCount: number
}

/**
 * Hook for recording a view on a published planner
 *
 * @returns Mutation object - call mutate(plannerId) to record view
 *
 * @example
 * ```tsx
 * function PlannerDetailPage({ plannerId }) {
 *   const recordView = usePlannerViewMutation();
 *
 *   useEffect(() => {
 *     recordView.mutate(plannerId);
 *   }, [plannerId]);
 *   // Note: recordView NOT in deps - prevents infinite loop
 *
 *   // View count updates automatically via cache invalidation
 * }
 * ```
 */
export function usePlannerViewMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (plannerId: string): Promise<ViewCountResponse> => {
      return await ApiClient.post(`/api/planner/md/${plannerId}/view`)
    },
    onSuccess: (_data, plannerId) => {
      // Invalidate published planner query to refetch with updated view count
      void queryClient.invalidateQueries({
        queryKey: publishedPlannerQueryKeys.detail(plannerId),
      })
    },
    retry: false,
    // Silently ignore errors - view recording is non-critical
  })
}

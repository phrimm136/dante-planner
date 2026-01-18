/**
 * Planner Publish Mutation Hook
 *
 * Handles publishing/unpublishing a planner.
 * Toggle endpoint - calling again unpublishes.
 * Only the planner owner can publish/unpublish.
 * Invalidates planner list cache on success.
 *
 * Pattern: usePlannerFork.ts (mutation + cache invalidation)
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { requestNotificationPermission } from '@/lib/browserNotification'
import { gesellschaftQueryKeys } from './useMDGesellschaftData'

// ============================================================================
// Response Type (simple toggle response)
// ============================================================================

interface PublishResponse {
  /** ID of the planner */
  plannerId: string
  /** New publish state */
  published: boolean
}

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook for publishing/unpublishing owned planners
 *
 * @example
 * ```tsx
 * function MyPlannerCard({ planner }) {
 *   const publish = usePlannerPublish();
 *
 *   const handleTogglePublish = () => {
 *     publish.mutate(planner.id);
 *   };
 *
 *   return (
 *     <button
 *       onClick={handleTogglePublish}
 *       disabled={publish.isPending}
 *     >
 *       {planner.isPublished ? 'Unpublish' : 'Publish'}
 *     </button>
 *   );
 * }
 * ```
 */
export function usePlannerPublish() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (plannerId: string): Promise<PublishResponse> => {
      // PUT toggles publish state
      const data = await ApiClient.put<PublishResponse>(`/api/planner/md/${plannerId}/publish`)
      return data
    },
    onSuccess: (response) => {
      // Invalidate all planner list queries to refresh publish state
      void queryClient.invalidateQueries({ queryKey: gesellschaftQueryKeys.all })

      // Request browser notification permission when publishing (not unpublishing)
      if (response.published) {
        void requestNotificationPermission()
      }
    },
    onError: (error) => {
      console.error('Publish toggle failed:', error)
    },
  })
}

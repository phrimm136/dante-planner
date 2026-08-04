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
import { validateData } from '@/lib/validation'
import { requestNotificationPermission } from '@/shared/notifications'
import { ServerPlannerResponseSchema } from '../schemas/PlannerSchemas'
import { gesellschaftQueryKeys } from './useMDGesellschaftData'

import type { ServerPlannerResponse } from '../types/PlannerTypes'

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
/** The state the caller wants the planner to end in, not a flip of whatever it is now. */
interface PublishVariables {
  plannerId: string
  published: boolean
}

export function usePlannerPublish() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({
      plannerId,
      published,
    }: PublishVariables): Promise<ServerPlannerResponse> => {
      const data = await ApiClient.put(`/api/planner/md/${plannerId}/publish`, { published })
      return validateData(data, ServerPlannerResponseSchema, 'planner publish')
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

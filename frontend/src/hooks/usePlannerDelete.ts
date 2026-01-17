/**
 * Planner Delete Mutation Hook
 *
 * Handles deleting user's own planners.
 * Backend auto-unpublishes before deleting (soft delete).
 * Invalidates both gesellschaft and user planners cache on success.
 * Caller provides onSuccess callback for navigation.
 *
 * Pattern: usePlannerFork.ts (mutation with onSuccess callback)
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { gesellschaftQueryKeys } from './useMDGesellschaftData'
import { userPlannersQueryKeys } from './useMDUserPlannersData'

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook for deleting user's planners
 *
 * @example
 * ```tsx
 * function PlannerCard({ planner }) {
 *   const deletePlanner = usePlannerDelete();
 *   const navigate = useNavigate();
 *
 *   const handleDelete = () => {
 *     deletePlanner.mutate(planner.id, {
 *       onSuccess: () => {
 *         // Navigate back to list after deletion
 *         navigate({ to: '/planner/md' });
 *       },
 *     });
 *   };
 *
 *   return (
 *     <button onClick={handleDelete} disabled={deletePlanner.isPending}>
 *       Delete
 *     </button>
 *   );
 * }
 * ```
 */
export function usePlannerDelete() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (plannerId: string): Promise<void> => {
      await ApiClient.delete(`/api/planner/md/${plannerId}`)
      // 204 No Content - no response body needed
    },
    onSuccess: () => {
      // Invalidate all planner list queries (both community and personal)
      void queryClient.invalidateQueries({ queryKey: gesellschaftQueryKeys.all })
      void queryClient.invalidateQueries({ queryKey: userPlannersQueryKeys.all })
    },
    onError: (error) => {
      console.error('Delete failed:', error)
    },
  })
}

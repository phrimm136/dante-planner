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

import { ApiClient, NotFoundError } from '@/lib/api'
import { gesellschaftQueryKeys } from './useMDGesellschaftData'

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
      try {
        await ApiClient.delete(`/api/planner/md/${plannerId}`)
      } catch (error) {
        // 404 means the row is already gone (retry after a successful first DELETE,
        // or a concurrent delete from another device). Treat as success so callers
        // see a consistent post-delete state.
        if (error instanceof NotFoundError) return
        throw error
      }
    },
    onSuccess: () => {
      // Invalidate community list (server-side concern)
      void queryClient.invalidateQueries({ queryKey: gesellschaftQueryKeys.all })
    },
    onError: (error) => {
      console.error('Delete failed:', error)
    },
  })
}

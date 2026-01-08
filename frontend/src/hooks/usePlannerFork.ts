/**
 * Planner Fork Mutation Hook
 *
 * Handles forking (copying) a community planner.
 * Creates a new draft planner owned by the current user.
 * Invalidates planner list cache on success.
 *
 * Pattern: usePlannerBookmark.ts (mutation + cache invalidation)
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { ForkResponseSchema } from '@/schemas/PlannerListSchemas'
import { gesellschaftQueryKeys } from './useMDGesellschaftData'

import type { ForkResponse } from '@/types/PlannerListTypes'

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook for forking community planners
 *
 * @example
 * ```tsx
 * function PlannerCard({ planner }) {
 *   const fork = usePlannerFork();
 *   const navigate = useNavigate();
 *
 *   const handleFork = () => {
 *     fork.mutate(planner.id, {
 *       onSuccess: (result) => {
 *         // Navigate to the new forked planner
 *         navigate({ to: '/planner/md/$id', params: { id: result.newPlannerId } });
 *       },
 *     });
 *   };
 *
 *   return (
 *     <button onClick={handleFork} disabled={fork.isPending}>
 *       Fork
 *     </button>
 *   );
 * }
 * ```
 */
export function usePlannerFork() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (plannerId: string): Promise<ForkResponse> => {
      const data = await ApiClient.post(`/api/planner/md/${plannerId}/fork`)
      const result = ForkResponseSchema.safeParse(data)

      if (!result.success) {
        console.error('Fork response validation failed:', result.error)
        throw new Error('Invalid fork response from server')
      }

      return result.data
    },
    onSuccess: () => {
      // Invalidate all planner list queries (my plans will have new entry)
      void queryClient.invalidateQueries({ queryKey: gesellschaftQueryKeys.all })
    },
    onError: (error) => {
      console.error('Fork failed:', error)
    },
  })
}

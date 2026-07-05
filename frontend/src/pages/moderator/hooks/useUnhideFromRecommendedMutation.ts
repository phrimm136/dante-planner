/**
 * Unhide From Recommended Mutation Hook
 *
 * Moderator action to restore a planner to recommended list.
 * Requires ROLE_MODERATOR or ROLE_ADMIN authorization.
 *
 * Pattern: usePlannerVote.ts (useMutation, multiple query invalidations)
 */

import { ApiClient } from '@/lib/api'
import { useApiMutation } from '@/components/hooks/useApiMutation'

// ============================================================================
// Mutation Input
// ============================================================================

export interface UnhideFromRecommendedInput {
  /** ID of the planner to unhide */
  plannerId: string
}

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook for unhiding planner from recommended list (moderator/admin only)
 *
 * @example
 * ```tsx
 * function HiddenPlannerList({ planner }) {
 *   const unhideMutation = useUnhideFromRecommendedMutation();
 *
 *   const handleUnhide = () => {
 *     unhideMutation.mutate({ plannerId: planner.id });
 *   };
 *
 *   return <button onClick={handleUnhide}>Restore to Recommended</button>;
 * }
 * ```
 */
export function useUnhideFromRecommendedMutation() {
  return useApiMutation<void, UnhideFromRecommendedInput>({
    mutationFn: async ({ plannerId }: UnhideFromRecommendedInput): Promise<void> => {
      await ApiClient.post(`/api/moderator/planner/${plannerId}/unhide-from-recommended`, {})
    },
    // Invalidate planner list queries to show unhidden planner in recommended
    invalidateKeys: () => [['planner'], ['gesellschaft']],
    errorLogPrefix: 'Unhide from recommended failed',
  })
}

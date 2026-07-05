/**
 * Hide From Recommended Mutation Hook
 *
 * Moderator action to hide a planner from recommended list.
 * Requires ROLE_MODERATOR or ROLE_ADMIN authorization.
 *
 * Pattern: usePlannerVote.ts (useMutation with request body)
 */

import { ApiClient } from '@/lib/api'
import { useApiMutation } from '@/components/hooks/useApiMutation'

// ============================================================================
// Mutation Input
// ============================================================================

export interface HideFromRecommendedInput {
  /** ID of the planner to hide */
  plannerId: string
  /** Reason for hiding from recommended */
  reason: string
}

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook for hiding planner from recommended list (moderator/admin only)
 *
 * @example
 * ```tsx
 * function ModerationPanel({ planner }) {
 *   const hideMutation = useHideFromRecommendedMutation();
 *
 *   const handleHide = (reason: string) => {
 *     hideMutation.mutate({ plannerId: planner.id, reason });
 *   };
 *
 *   return <button onClick={() => handleHide("Policy violation")}>Hide</button>;
 * }
 * ```
 */
export function useHideFromRecommendedMutation() {
  return useApiMutation<void, HideFromRecommendedInput>({
    mutationFn: async ({ plannerId, reason }: HideFromRecommendedInput): Promise<void> => {
      await ApiClient.post(`/api/moderator/planner/${plannerId}/hide-from-recommended`, { reason })
    },
    // Invalidate planner list queries to remove hidden planner from recommended
    invalidateKeys: () => [['planner'], ['gesellschaft']],
    errorLogPrefix: 'Hide from recommended failed',
  })
}

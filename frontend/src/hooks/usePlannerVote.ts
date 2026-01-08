/**
 * Planner Vote Mutation Hook
 *
 * Handles voting on community planners (upvote/downvote/remove).
 * Invalidates planner list cache on success.
 *
 * Pattern: useAuthQuery.ts (useMutation + cache invalidation)
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { VoteResponseSchema } from '@/schemas/PlannerListSchemas'
import { gesellschaftQueryKeys } from './useMDGesellschaftData'

import type { VoteDirection, VoteResponse } from '@/types/PlannerListTypes'

// ============================================================================
// Mutation Input
// ============================================================================

export interface VotePlannerInput {
  /** ID of the planner to vote on */
  plannerId: string
  /** Vote direction (UP/DOWN) or null to remove vote */
  voteType: VoteDirection | null
}

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook for voting on community planners
 *
 * @example
 * ```tsx
 * function PlannerCard({ planner }) {
 *   const vote = usePlannerVote();
 *
 *   const handleUpvote = () => {
 *     // Toggle: if already upvoted, remove vote; else upvote
 *     const newVote = planner.userVote === 'UP' ? null : 'UP';
 *     vote.mutate({ plannerId: planner.id, voteType: newVote });
 *   };
 *
 *   return (
 *     <button
 *       onClick={handleUpvote}
 *       disabled={vote.isPending}
 *     >
 *       {planner.upvoteCount}
 *     </button>
 *   );
 * }
 * ```
 */
export function usePlannerVote() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({ plannerId, voteType }: VotePlannerInput): Promise<VoteResponse> => {
      const data = await ApiClient.post(`/api/planner/md/${plannerId}/vote`, { voteType })
      const result = VoteResponseSchema.safeParse(data)

      if (!result.success) {
        console.error('Vote response validation failed:', result.error)
        throw new Error('Invalid vote response from server')
      }

      return result.data
    },
    onSuccess: () => {
      // Invalidate all planner list queries to refresh vote counts
      void queryClient.invalidateQueries({ queryKey: gesellschaftQueryKeys.all })
    },
    onError: (error) => {
      console.error('Vote failed:', error)
    },
  })
}

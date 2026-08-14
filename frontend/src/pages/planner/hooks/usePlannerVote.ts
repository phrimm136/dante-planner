/**
 * Planner Vote Mutation Hook
 *
 * Handles upvoting community planners.
 * BREAKING: Upvote-only system - votes are immutable and cannot be changed or removed.
 * Invalidates planner list cache on success.
 *
 * Pattern: useAuthQuery.ts (useMutation + cache invalidation)
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiClient, ConflictError } from '@/lib/api'
import { showErrorMessage } from '@/lib/errorPresentation'
import { validateData } from '@/lib/validation'
import { VoteResponseSchema } from '../schemas/PlannerListSchemas'
import { gesellschaftQueryKeys } from './useMDGesellschaftData'
import { publishedPlannerQueryKeys } from './usePublishedPlannerQuery'

import type { VoteResponse } from '../types/PlannerListTypes'

/** Vote direction - only upvotes supported */
type VoteDirection = 'UP'

// ============================================================================
// Mutation Input
// ============================================================================

export interface VotePlannerInput {
  /** ID of the planner to vote on */
  plannerId: string
  /** Vote direction (UP only) - null not allowed (votes are immutable) */
  voteType: VoteDirection
}

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook for upvoting community planners
 *
 * BREAKING: Votes are immutable - once cast, they cannot be changed or removed.
 * BREAKING: Only upvotes are supported - downvoting has been removed.
 * Attempting to vote again will result in a 409 Conflict error.
 *
 * @example
 * ```tsx
 * function PlannerCard({ planner }) {
 *   const vote = usePlannerVote();
 *
 *   const handleUpvote = () => {
 *     // Check if already voted
 *     if (planner.hasUpvoted) {
 *       console.error('Already voted - votes are permanent');
 *       return;
 *     }
 *     vote.mutate({ plannerId: planner.id, voteType: 'UP' });
 *   };
 *
 *   return (
 *     <button
 *       onClick={handleUpvote}
 *       disabled={vote.isPending || planner.hasUpvoted}
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
      const data = await ApiClient.post(`/api/planner/md/${plannerId}/upvote`, { voteType })
      return validateData(data, VoteResponseSchema, 'planner vote')
    },
    onSuccess: (response, { plannerId }) => {
      // Optimistically update cache with response data
      queryClient.setQueryData(publishedPlannerQueryKeys.detail(plannerId), (old: any) => {
        if (!old?.apiData) return old
        return {
          ...old,
          apiData: {
            ...old.apiData,
            upvotes: response.upvoteCount,
            hasUpvoted: response.hasUpvoted,
          },
        }
      })

      // Also invalidate list queries to refresh cards
      void queryClient.invalidateQueries({ queryKey: gesellschaftQueryKeys.all })
    },
    onError: (error) => {
      if (error instanceof ConflictError) {
        showErrorMessage('planner:pages.plannerList.contextMenu.alreadyVoted')
      }
    },
  })
}

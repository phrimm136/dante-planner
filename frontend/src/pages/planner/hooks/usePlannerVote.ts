import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { ConflictError } from '@/lib/apiErrors'
import { showError, showErrorMessage } from '@/lib/errorPresentation'
import { validateData } from '@/lib/validation'
import { VoteResponseSchema } from '../schemas/PlannerListSchemas'
import { useInvalidatePlannerLists } from './useInvalidatePlannerLists'
import { publishedPlannerQueryKeys, isPlannerRemoved } from './usePublishedPlannerQuery'
import type { PublishedPlannerQueryState } from './usePublishedPlannerQuery'

import type { VoteResponse } from '../types/PlannerListTypes'

type VoteDirection = 'UP'

export interface VotePlannerInput {
  plannerId: string
  voteType: VoteDirection
}

export function usePlannerVote() {
  const queryClient = useQueryClient()
  const invalidatePlannerLists = useInvalidatePlannerLists()

  return useMutation({
    mutationFn: async ({ plannerId, voteType }: VotePlannerInput): Promise<VoteResponse> => {
      const data = await ApiClient.post(`/api/planner/md/${plannerId}/upvote`, { voteType })
      return validateData(data, VoteResponseSchema, 'planner vote')
    },
    onSuccess: (response, { plannerId }) => {
      queryClient.setQueryData(
        publishedPlannerQueryKeys.detail(plannerId),
        (old: PublishedPlannerQueryState | undefined) => {
          if (!old || isPlannerRemoved(old)) return old
          return {
            ...old,
            apiData: {
              ...old.apiData,
              upvotes: response.upvoteCount,
              hasUpvoted: response.hasUpvoted,
            },
          }
        },
      )

      invalidatePlannerLists()
    },
    meta: { suppressErrorToast: true },
    onError: (error) => {
      if (error instanceof ConflictError) {
        showErrorMessage('planner:toast.alreadyVoted')
        return
      }
      showError(error)
    },
  })
}

import { useMutation } from '@tanstack/react-query'
import { ApiClient } from '@/lib/api'
import { useInvalidatePlannerLists } from './useInvalidatePlannerLists'

interface ModeratorDeleteRequest {
  plannerId: string
  reason: string
}

export function useModeratorPlannerDelete() {
  const invalidatePlannerLists = useInvalidatePlannerLists()

  return useMutation({
    mutationFn: async ({ plannerId, reason }: ModeratorDeleteRequest) => {
      await ApiClient.post(`/api/moderation/planner/${plannerId}/takedown`, { reason })
    },
    onSuccess: () => {
      invalidatePlannerLists()
    },
  })
}

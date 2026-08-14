import { useMutation } from '@tanstack/react-query'
import { ApiClient } from '@/lib/api'
import { useInvalidatePlannerLists } from './useInvalidatePlannerLists'

interface ModeratorDeleteRequest {
  plannerId: string
  /** Recorded in the moderation audit trail; the server rejects a blank one. */
  reason: string
}

/**
 * Hook for moderator planner takedown (POST /api/moderation/planner/{id}/takedown).
 * Takes down planner from public but allows owner to keep syncing their local copy.
 */
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

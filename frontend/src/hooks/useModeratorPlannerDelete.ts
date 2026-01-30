import { useMutation } from '@tanstack/react-query'
import { ApiClient } from '@/lib/api'

interface ModeratorDeleteRequest {
  plannerId: string
  reason?: string
}

/**
 * Hook for moderator planner takedown (POST /api/moderation/planner/{id}/takedown).
 * Takes down planner from public but allows owner to keep syncing their local copy.
 */
export function useModeratorPlannerDelete() {
  return useMutation({
    mutationFn: async ({ plannerId, reason }: ModeratorDeleteRequest) => {
      await ApiClient.post(`/api/moderation/planner/${plannerId}/takedown`, {
        reason: reason || '',
      })
    },
  })
}

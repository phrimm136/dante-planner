import { useMutation } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { NotFoundError, UnauthorizedError } from '@/lib/apiErrors'
import { useInvalidatePlannerLists } from './useInvalidatePlannerLists'

/** The server's answer; only `deleted` means the row was there and is now gone. */
export type DeleteOutcome = 'deleted' | 'alreadyGone' | 'unauthorized'

export function usePlannerDelete() {
  const invalidatePlannerLists = useInvalidatePlannerLists()

  return useMutation({
    mutationFn: async (plannerId: string): Promise<DeleteOutcome> => {
      try {
        await ApiClient.delete(`/api/planner/md/${plannerId}`)
        return 'deleted'
      } catch (error) {
        if (error instanceof NotFoundError) return 'alreadyGone'
        if (error instanceof UnauthorizedError) return 'unauthorized'
        throw error
      }
    },
    onSuccess: () => {
      invalidatePlannerLists()
    },
    onError: (error) => {
      console.error('Delete failed:', error)
    },
  })
}

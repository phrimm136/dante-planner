import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { plannerQueryKeys } from '../lib/plannerQueryKeys'
import { gesellschaftQueryKeys } from './useMDGesellschaftData'
import { userPlannersQueryKeys } from './useMDUserPlannersData'

/**
 * Invalidates every list a planner can appear in.
 *
 * A planner is listed in three places under three key roots, and a mutation
 * rarely knows which of them its effect reaches — a takedown drops a plan from
 * the community list, a publish adds it there and changes it in the owner's.
 * Callers invalidate all three and leave detail keys to the site that owns them.
 */
export function useInvalidatePlannerLists() {
  const queryClient = useQueryClient()

  return useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: plannerQueryKeys.list() })
    void queryClient.invalidateQueries({ queryKey: gesellschaftQueryKeys.all })
    void queryClient.invalidateQueries({ queryKey: userPlannersQueryKeys.all })
  }, [queryClient])
}

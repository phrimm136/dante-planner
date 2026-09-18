import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { plannerQueryKeys } from '../lib/plannerQueryKeys'
import { gesellschaftQueryKeys } from './useMDGesellschaftData'
import { userPlannersQueryKeys } from './useMDUserPlannersData'

export function useInvalidatePlannerLists() {
  const queryClient = useQueryClient()

  return useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: plannerQueryKeys.list() })
    void queryClient.invalidateQueries({ queryKey: gesellschaftQueryKeys.all })
    void queryClient.invalidateQueries({ queryKey: userPlannersQueryKeys.all })
  }, [queryClient])
}

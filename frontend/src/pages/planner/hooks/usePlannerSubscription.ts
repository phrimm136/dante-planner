import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { validateData } from '@/lib/validation'
import { SubscriptionResponseSchema } from '../schemas/PlannerListSchemas'
import { publishedPlannerQueryKeys } from './usePublishedPlannerQuery'

import type { SubscriptionResponse } from '../types/PlannerListTypes'

export function usePlannerSubscription() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (plannerId: string): Promise<SubscriptionResponse> => {
      const data = await ApiClient.post(`/api/planner/md/${plannerId}/subscribe`)
      return validateData(data, SubscriptionResponseSchema, 'planner subscription')
    },
    onSuccess: (_data, plannerId) => {
      void queryClient.invalidateQueries({ queryKey: publishedPlannerQueryKeys.detail(plannerId) })
    },
    onError: (error) => {
      console.error('Subscription failed:', error)
    },
  })
}

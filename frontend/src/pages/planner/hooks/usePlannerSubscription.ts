import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { validateData } from '@/lib/validation'
import { SubscriptionResponseSchema } from '../schemas/PlannerListSchemas'
import { publishedPlannerQueryKeys, isPlannerRemoved } from './usePublishedPlannerQuery'
import type { PublishedPlannerQueryState } from './usePublishedPlannerQuery'

import type { SubscriptionResponse } from '../types/PlannerListTypes'

export type SubscriptionIntent = 'subscribe' | 'unsubscribe'

export interface PlannerSubscriptionInput {
  plannerId: string
  intent: SubscriptionIntent
}

function sendIntent({ plannerId, intent }: PlannerSubscriptionInput): Promise<unknown> {
  const path = `/api/planner/md/${plannerId}/subscription`
  return intent === 'subscribe' ? ApiClient.put(path) : ApiClient.delete(path)
}

export function usePlannerSubscription() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (input: PlannerSubscriptionInput): Promise<SubscriptionResponse> => {
      const data = await sendIntent(input)
      return validateData(data, SubscriptionResponseSchema, 'planner subscription')
    },
    onSuccess: (response, { plannerId }) => {
      queryClient.setQueryData(
        publishedPlannerQueryKeys.detail(plannerId),
        (old: PublishedPlannerQueryState | undefined) => {
          if (!old || isPlannerRemoved(old)) return old
          return { ...old, apiData: { ...old.apiData, isSubscribed: response.subscribed } }
        },
      )
    },
    onError: (error) => {
      console.error('Subscription failed:', error)
    },
  })
}

import { useMutation } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { validateData } from '@/lib/validation'
import { requestNotificationPermission } from '@/shared/notifications'
import { ServerPlannerResponseSchema } from '../schemas/PlannerSchemas'
import { useInvalidatePlannerLists } from './useInvalidatePlannerLists'

import type { ServerPlannerResponse } from '../types/PlannerTypes'

interface PublishVariables {
  plannerId: string
  published: boolean
}

export function usePlannerPublish() {
  const invalidatePlannerLists = useInvalidatePlannerLists()

  return useMutation({
    mutationFn: async ({
      plannerId,
      published,
    }: PublishVariables): Promise<ServerPlannerResponse> => {
      const intent = published ? 'publish' : 'unpublish'
      const data = await ApiClient.post(`/api/planner/md/${plannerId}/${intent}`)
      return validateData(data, ServerPlannerResponseSchema, 'planner publish')
    },
    onSuccess: (response) => {
      invalidatePlannerLists()

      if (response.published) {
        void requestNotificationPermission()
      }
    },
    onError: (error) => {
      console.error('Publish toggle failed:', error)
    },
  })
}

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { z } from 'zod'

import { ApiClient } from '@/lib/api'
import { validateData } from '@/lib/validation'
import { publishedPlannerQueryKeys } from './usePublishedPlannerQuery'

interface ToggleOwnerNotificationsInput {
  plannerId: string
  enabled: boolean
}

const ToggleOwnerNotificationsResponseSchema = z
  .object({
    ownerNotificationsEnabled: z.boolean(),
  })
  .strict()

type ToggleOwnerNotificationsResponse = z.infer<typeof ToggleOwnerNotificationsResponseSchema>

export function useToggleOwnerNotifications() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({
      plannerId,
      enabled,
    }: ToggleOwnerNotificationsInput): Promise<ToggleOwnerNotificationsResponse> => {
      const data = await ApiClient.patch(`/api/planner/md/${plannerId}/notifications`, {
        enabled,
      })
      return validateData(
        data,
        ToggleOwnerNotificationsResponseSchema,
        'planner owner notifications',
      )
    },
    onSuccess: (_, { plannerId }) => {
      void queryClient.invalidateQueries({
        queryKey: publishedPlannerQueryKeys.detail(plannerId),
      })
    },
    onError: (error) => {
      console.error('Toggle owner notifications failed:', error)
    },
  })
}

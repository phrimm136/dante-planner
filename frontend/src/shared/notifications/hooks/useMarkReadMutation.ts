import { ApiClient } from '@/lib/api'
import { validateData } from '@/lib/validation'
import { NotificationResponseSchema } from '../schemas/NotificationSchemas'
import { useApiMutation } from '@/components/hooks/useApiMutation'
import { notificationQueryKeys } from './useNotificationsQuery'

import type { NotificationResponse } from '../types/NotificationTypes'

export interface MarkReadInput {
  notificationId: string
}

export function useMarkReadMutation() {
  return useApiMutation<NotificationResponse, MarkReadInput>({
    mutationFn: async ({ notificationId }: MarkReadInput): Promise<NotificationResponse> => {
      const data = await ApiClient.post(`/api/notifications/${notificationId}/mark-read`, {})
      return validateData(data, NotificationResponseSchema, 'notifications markRead')
    },
    invalidateKeys: () => [notificationQueryKeys.all],
  })
}

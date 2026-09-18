import { ApiClient } from '@/lib/api'
import { useApiMutation } from '@/components/hooks/useApiMutation'
import { validateData } from '@/lib/validation'
import { NotificationBulkResultResponseSchema } from '../schemas/NotificationSchemas'
import { notificationQueryKeys } from './useNotificationsQuery'

import type { NotificationBulkResultResponse } from '../schemas/NotificationSchemas'

export function useClearAllNotificationsMutation() {
  return useApiMutation<NotificationBulkResultResponse>({
    mutationFn: async (): Promise<NotificationBulkResultResponse> => {
      const data = await ApiClient.delete('/api/notifications/all')
      return validateData(data, NotificationBulkResultResponseSchema, 'notifications clear all')
    },
    invalidateKeys: () => [notificationQueryKeys.all],
  })
}

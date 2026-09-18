import { ApiClient } from '@/lib/api'
import { useApiMutation } from '@/components/hooks/useApiMutation'
import { notificationQueryKeys } from './useNotificationsQuery'

export function useDeleteNotificationMutation() {
  return useApiMutation<void, string>({
    mutationFn: async (notificationId: string): Promise<void> => {
      await ApiClient.delete(`/api/notifications/${notificationId}`)
    },
    invalidateKeys: () => [notificationQueryKeys.all],
  })
}

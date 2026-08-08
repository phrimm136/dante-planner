/**
 * Clear All Notifications Mutation Hook
 *
 * Deletes all notifications for the current user (clears entire inbox).
 * Backend returns the number of notifications it removed.
 *
 * Pattern: useDeleteNotificationMutation.ts (useMutation with cache invalidation)
 */

import { ApiClient } from '@/lib/api'
import { useApiMutation } from '@/components/hooks/useApiMutation'
import { validateData } from '@/lib/validation'
import { NotificationBulkResultResponseSchema } from '../schemas/NotificationSchemas'
import { notificationQueryKeys } from './useNotificationsQuery'

import type { NotificationBulkResultResponse } from '../schemas/NotificationSchemas'

/**
 * Hook for clearing all notifications in user's inbox
 *
 * @example
 * ```tsx
 * function NotificationDialog() {
 *   const clearAll = useClearAllNotificationsMutation();
 *
 *   const handleClearAll = () => {
 *     clearAll.mutate();
 *   };
 *
 *   return (
 *     <button onClick={handleClearAll} disabled={clearAll.isPending}>
 *       Clear All
 *     </button>
 *   );
 * }
 * ```
 */
export function useClearAllNotificationsMutation() {
  return useApiMutation<NotificationBulkResultResponse>({
    mutationFn: async (): Promise<NotificationBulkResultResponse> => {
      const data = await ApiClient.delete('/api/notifications/all')
      return validateData(data, NotificationBulkResultResponseSchema, 'notifications clear all')
    },
    // Invalidate all notification queries (inbox list and unread count)
    invalidateKeys: () => [notificationQueryKeys.all],
    errorLogPrefix: 'Clear all notifications failed',
  })
}

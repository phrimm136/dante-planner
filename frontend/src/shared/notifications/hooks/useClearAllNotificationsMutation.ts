/**
 * Clear All Notifications Mutation Hook
 *
 * Deletes all notifications for the current user (clears entire inbox).
 * Backend returns 204 No Content.
 *
 * Pattern: useDeleteNotificationMutation.ts (useMutation with cache invalidation)
 */

import { ApiClient } from '@/lib/api'
import { useApiMutation } from '@/components/hooks/useApiMutation'
import { notificationQueryKeys } from './useNotificationsQuery'

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
  return useApiMutation<void>({
    mutationFn: async (): Promise<void> => {
      await ApiClient.delete('/api/notifications/all')
    },
    // Invalidate all notification queries (inbox list and unread count)
    invalidateKeys: () => [notificationQueryKeys.all],
    errorLogPrefix: 'Clear all notifications failed',
  })
}

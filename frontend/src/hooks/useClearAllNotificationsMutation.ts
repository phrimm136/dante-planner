/**
 * Clear All Notifications Mutation Hook
 *
 * Deletes all notifications for the current user (clears entire inbox).
 * Backend returns 204 No Content.
 *
 * Pattern: useDeleteNotificationMutation.ts (useMutation with cache invalidation)
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
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
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (): Promise<void> => {
      await ApiClient.delete('/api/notifications/all')
    },
    onSuccess: () => {
      // Invalidate all notification queries (inbox list and unread count)
      void queryClient.invalidateQueries({ queryKey: notificationQueryKeys.all })
    },
    onError: (error) => {
      console.error('Clear all notifications failed:', error)
    },
  })
}

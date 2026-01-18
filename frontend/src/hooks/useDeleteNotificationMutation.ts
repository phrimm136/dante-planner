/**
 * Delete Notification Mutation Hook
 *
 * Soft-deletes a notification (dismisses from inbox).
 * Backend returns 204 No Content.
 *
 * Pattern: usePlannerVote.ts (useMutation with no response body)
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { notificationQueryKeys } from './useNotificationsQuery'

// ============================================================================
// Mutation Input
// ============================================================================

export interface DeleteNotificationInput {
  /** Public UUID of the notification to delete */
  notificationId: string
}

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook for deleting (dismissing) a notification
 *
 * @example
 * ```tsx
 * function NotificationItem({ notification }) {
 *   const deleteNotification = useDeleteNotificationMutation();
 *
 *   const handleDismiss = () => {
 *     deleteNotification.mutate(notification.id);
 *   };
 *
 *   return <button onClick={handleDismiss}>Dismiss</button>;
 * }
 * ```
 */
export function useDeleteNotificationMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (notificationId: string): Promise<void> => {
      await ApiClient.delete(`/api/notifications/${notificationId}`)
    },
    onSuccess: () => {
      // Invalidate notifications list and unread count
      void queryClient.invalidateQueries({ queryKey: notificationQueryKeys.all })
    },
    onError: (error) => {
      console.error('Delete notification failed:', error)
    },
  })
}

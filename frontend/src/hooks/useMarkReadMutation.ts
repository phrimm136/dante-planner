/**
 * Mark Notification Read Mutation Hook
 *
 * Marks a single notification as read.
 * Invalidates notification queries on success.
 *
 * Pattern: usePlannerVote.ts (useMutation + query invalidation)
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { NotificationResponseSchema } from '@/schemas/NotificationSchemas'
import { notificationQueryKeys } from './useNotificationsQuery'

import type { NotificationResponse } from '@/types/NotificationTypes'

// ============================================================================
// Mutation Input
// ============================================================================

export interface MarkReadInput {
  /** ID of the notification to mark as read */
  notificationId: number
}

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook for marking notification as read
 *
 * @example
 * ```tsx
 * function NotificationItem({ notification }) {
 *   const markRead = useMarkReadMutation();
 *
 *   const handleClick = () => {
 *     if (!notification.read) {
 *       markRead.mutate({ notificationId: notification.id });
 *     }
 *   };
 *
 *   return <div onClick={handleClick}>...</div>;
 * }
 * ```
 */
export function useMarkReadMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({ notificationId }: MarkReadInput): Promise<NotificationResponse> => {
      const data = await ApiClient.post(`/api/notifications/${notificationId}/mark-read`, {})
      const result = NotificationResponseSchema.safeParse(data)

      if (!result.success) {
        console.error('Mark read response validation failed:', result.error)
        throw new Error('Invalid mark read response from server')
      }

      return result.data
    },
    onSuccess: () => {
      // Invalidate notifications list and unread count
      void queryClient.invalidateQueries({ queryKey: notificationQueryKeys.all })
    },
    onError: (error) => {
      console.error('Mark read failed:', error)
    },
  })
}

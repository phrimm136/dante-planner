/**
 * Notifications Query Hook
 *
 * Fetches paginated notifications for the current user's inbox.
 * Uses useSuspenseQuery for consistent loading states with Suspense boundaries.
 *
 * Pattern: useIdentityListData.ts (useSuspenseQuery + validation)
 */

import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { validateData } from '@/lib/validation'
import { NotificationInboxResponseSchema } from '../schemas/NotificationSchemas'

import type { NotificationInboxResponse } from '../types/NotificationTypes'
import { STALE_TIME } from '@/lib/constants'

// ============================================================================
// Query Key Factory
// ============================================================================

export const notificationQueryKeys = {
  all: ['notifications'] as const,
  inbox: (page: number, size: number) => ['notifications', 'inbox', page, size] as const,
  unreadCount: () => ['notifications', 'unread-count'] as const,
}

// ============================================================================
// Query Options
// ============================================================================

function createNotificationsQueryOptions(page: number = 0, size: number = 20) {
  return queryOptions({
    queryKey: notificationQueryKeys.inbox(page, size),
    queryFn: async ({ signal }): Promise<NotificationInboxResponse> => {
      const data = await ApiClient.get(`/api/notifications/inbox?page=${page}&size=${size}`, {
        signal,
      })
      return validateData(data, NotificationInboxResponseSchema, 'notifications inbox')
    },
    staleTime: STALE_TIME.SHORT,
  })
}

// ============================================================================
// Hook
// ============================================================================

/**
 * Hook for fetching notifications inbox
 *
 * Uses useSuspenseQuery - wrap in Suspense boundary for loading states.
 *
 * @param page - Page number (0-indexed)
 * @param size - Page size (default: 20)
 * @returns Query result with notifications data
 *
 * @example
 * ```tsx
 * function NotificationList() {
 *   const { data } = useNotificationsQuery(0, 20);
 *
 *   return <div>{data.notifications.length} notifications</div>;
 * }
 *
 * // Wrap in Suspense
 * <Suspense fallback={<LoadingSpinner />}>
 *   <NotificationList />
 * </Suspense>
 * ```
 */
export function useNotificationsQuery(page: number = 0, size: number = 20) {
  const { data } = useSuspenseQuery(createNotificationsQueryOptions(page, size))
  return data
}

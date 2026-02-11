/**
 * Unread Count Query Hook
 *
 * Fetches unread notification count for the current user.
 * Refetches automatically when SSE notification events occur via query invalidation.
 *
 * Pattern: useIdentityListData.ts (SSE-driven updates)
 */

import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { UnreadCountResponseSchema } from '@/schemas/NotificationSchemas'
import { notificationQueryKeys } from './useNotificationsQuery'

import type { UnreadCountResponse } from '@/types/NotificationTypes'

// ============================================================================
// Query Options
// ============================================================================

function createUnreadCountQueryOptions() {
  return queryOptions({
    queryKey: notificationQueryKeys.unreadCount(),
    queryFn: async (): Promise<UnreadCountResponse> => {
      const data = await ApiClient.get('/api/notifications/unread-count')
      const result = UnreadCountResponseSchema.safeParse(data)

      if (!result.success) {
        console.error('Unread count response validation failed:', result.error)
        throw new Error('Invalid unread count response from server')
      }

      return result.data
    },
    staleTime: 5 * 60 * 1000, // 5 minutes (refetches on SSE notification events)
  })
}

// ============================================================================
// Hook
// ============================================================================

/**
 * Hook for fetching unread notification count
 *
 * Refetches automatically when SSE notification events occur (via useSseConnection).
 * Uses useSuspenseQuery - wrap in Suspense boundary for loading states.
 *
 * @returns Unread count data
 *
 * @example
 * ```tsx
 * function NotificationBell() {
 *   const { unreadCount } = useUnreadCountQuery();
 *
 *   return (
 *     <button>
 *       <BellIcon />
 *       {unreadCount > 0 && <Badge>{unreadCount}</Badge>}
 *     </button>
 *   );
 * }
 *
 * // Wrap in Suspense
 * <Suspense fallback={<BellIcon />}>
 *   <NotificationBell />
 * </Suspense>
 * ```
 */
export function useUnreadCountQuery() {
  const { data } = useSuspenseQuery(createUnreadCountQueryOptions())
  return data
}

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
import { validateData } from '@/lib/validation'
import { UnreadCountResponseSchema } from '../schemas/NotificationSchemas'
import { notificationQueryKeys } from './useNotificationsQuery'

import type { UnreadCountResponse } from '../types/NotificationTypes'
import { STALE_TIME } from '@/lib/constants'

// ============================================================================
// Query Options
// ============================================================================

function createUnreadCountQueryOptions() {
  return queryOptions({
    queryKey: notificationQueryKeys.unreadCount(),
    queryFn: async (): Promise<UnreadCountResponse> => {
      const data = await ApiClient.get('/api/notifications/unread-count')
      return validateData(data, UnreadCountResponseSchema, 'notifications unreadCount')
    },
    staleTime: STALE_TIME.MEDIUM,
  })
}

// ============================================================================
// Hook
// ============================================================================

/**
 * Hook for fetching unread notification count
 *
 * Refetches automatically when SSE notification events occur (via useAppSse).
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

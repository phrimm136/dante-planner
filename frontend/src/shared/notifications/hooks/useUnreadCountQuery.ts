import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { validateData } from '@/lib/validation'
import { UnreadCountResponseSchema } from '../schemas/NotificationSchemas'
import { notificationQueryKeys } from './useNotificationsQuery'

import type { UnreadCountResponse } from '../types/NotificationTypes'
import { STALE_TIME } from '@/lib/constants'

function createUnreadCountQueryOptions() {
  return queryOptions({
    queryKey: notificationQueryKeys.unreadCount(),
    queryFn: async ({ signal }): Promise<UnreadCountResponse> => {
      const data = await ApiClient.get('/api/notifications/unread-count', { signal })
      return validateData(data, UnreadCountResponseSchema, 'notifications unreadCount')
    },
    staleTime: STALE_TIME.MEDIUM,
  })
}

export function useUnreadCountQuery() {
  const { data } = useSuspenseQuery(createUnreadCountQueryOptions())
  return data
}

import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { validateData } from '@/lib/validation'
import { NotificationInboxResponseSchema } from '../schemas/NotificationSchemas'

import type { NotificationInboxResponse } from '../types/NotificationTypes'
import { STALE_TIME } from '@/lib/constants'

export const notificationQueryKeys = {
  all: ['notifications'] as const,
  inbox: (page: number, size: number) => ['notifications', 'inbox', page, size] as const,
  unreadCount: () => ['notifications', 'unread-count'] as const,
}

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

export function useNotificationsQuery(page: number = 0, size: number = 20) {
  const { data } = useSuspenseQuery(createNotificationsQueryOptions(page, size))
  return data
}

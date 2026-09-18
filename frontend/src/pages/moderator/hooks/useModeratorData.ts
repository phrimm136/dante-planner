import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { validateData } from '@/lib/validation'
import { UserForModSchema, ModerationActionSchema } from '../schemas/ModeratorSchemas'

import type { UserForMod, ModerationAction } from '../types/ModeratorTypes'
import { STALE_TIME } from '@/lib/constants'

export const moderatorQueryKeys = {
  all: ['moderator'] as const,
  users: () => ['moderator', 'users'] as const,
  actions: () => ['moderator', 'actions'] as const,
}

function createModeratorUsersQueryOptions() {
  return queryOptions({
    queryKey: moderatorQueryKeys.users(),
    queryFn: async ({ signal }): Promise<UserForMod[]> => {
      const data = await ApiClient.get('/api/moderation/users', { signal })
      return validateData(data, UserForModSchema.array(), 'moderation users')
    },
    staleTime: STALE_TIME.FREQUENT,
  })
}

function createModerationHistoryQueryOptions() {
  return queryOptions({
    queryKey: moderatorQueryKeys.actions(),
    queryFn: async ({ signal }): Promise<ModerationAction[]> => {
      const data = await ApiClient.get('/api/moderation/actions', { signal })
      return validateData(data, ModerationActionSchema.array(), 'moderation actions')
    },
    staleTime: STALE_TIME.LIVE,
  })
}

export function useModeratorUsers() {
  const { data } = useSuspenseQuery(createModeratorUsersQueryOptions())
  return data
}

export function useModerationHistory() {
  const { data } = useSuspenseQuery(createModerationHistoryQueryOptions())
  return data
}

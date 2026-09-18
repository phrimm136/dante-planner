import { useSuspenseQuery, useMutation, useQueryClient, queryOptions } from '@tanstack/react-query'
import { ApiClient } from '@/lib/api'
import { validateData } from '@/lib/validation'
import { EpithetListResponseSchema, UserDeletionResponseSchema } from '../schemas/AccountSchemas'
import { UserSchema } from '@/shared/auth'
import { authQueryKeys } from '@/shared/auth'
import type {
  EpithetListResponse,
  UpdateUsernameEpithetRequest,
  UserDeletionResponse,
} from '../types/UserSettingsTypes'
import type { User } from '@/shared/auth'
import { STALE_TIME } from '@/lib/constants'

export const accountQueryKeys = {
  epithets: () => ['user', 'epithets'] as const,
}

function createEpithetsQueryOptions() {
  return queryOptions({
    queryKey: accountQueryKeys.epithets(),
    queryFn: async ({ signal }): Promise<EpithetListResponse> => {
      const data = await ApiClient.get<EpithetListResponse>('/api/user/epithets', { signal })
      return validateData(data, EpithetListResponseSchema, 'user epithets')
    },
    staleTime: STALE_TIME.LONG,
  })
}

export function useEpithetsQuery() {
  const { data } = useSuspenseQuery(createEpithetsQueryOptions())
  return { epithets: data.epithets }
}

export function useUpdateEpithetMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (request: UpdateUsernameEpithetRequest): Promise<User> => {
      const data = await ApiClient.put<User>('/api/user/me/username-epithet', request)
      return validateData(data, UserSchema, 'user usernameEpithet')
    },
    onSuccess: (user) => {
      queryClient.setQueryData(authQueryKeys.me, user)
    },
    onError: (error) => {
      console.error('Failed to update username epithet:', error)
    },
  })
}

export function useDeleteAccountMutation() {
  return useMutation({
    mutationFn: async (): Promise<UserDeletionResponse> => {
      const data = await ApiClient.delete<UserDeletionResponse>('/api/user/me')
      return validateData(data, UserDeletionResponseSchema, 'user deletion')
    },
    onError: (error) => {
      console.error('Failed to delete account:', error)
    },
  })
}

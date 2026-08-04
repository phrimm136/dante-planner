import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { validateData } from '@/lib/validation'
import { UserSettingsResponseSchema } from '../schemas/UserSettingsSchemas'
import { useAuthQueryNonBlocking } from '@/shared/auth'
import type { UserSettingsResponse, UpdateUserSettingsRequest } from '../types/UserSettingsTypes'
import { STALE_TIME } from '@/lib/constants'

/**
 * Query keys for user settings queries
 */
export const userSettingsKeys = {
  settings: () => ['user', 'settings'] as const,
}

/**
 * Hook to fetch user settings (sync and notification preferences).
 * Only enabled when authenticated.
 *
 * @returns Query result with settings data, loading state, and error
 */
export function useUserSettingsQuery() {
  const { data: user } = useAuthQueryNonBlocking()
  const isAuthenticated = !!user

  return useQuery({
    queryKey: userSettingsKeys.settings(),
    queryFn: async (): Promise<UserSettingsResponse> => {
      const data = await ApiClient.get<UserSettingsResponse>('/api/user/settings')
      return validateData(data, UserSettingsResponseSchema, 'user settings')
    },
    enabled: isAuthenticated,
    staleTime: STALE_TIME.MEDIUM,
  })
}

/**
 * Hook for updating user settings mutation.
 * Invalidates settings cache on success.
 */
export function useUpdateUserSettingsMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (request: UpdateUserSettingsRequest): Promise<UserSettingsResponse> => {
      const data = await ApiClient.put<UserSettingsResponse>('/api/user/settings', request)
      return validateData(data, UserSettingsResponseSchema, 'user settings update')
    },
    onSuccess: (settings) => {
      queryClient.setQueryData(userSettingsKeys.settings(), settings)
    },
    onError: (error) => {
      console.error('Failed to update user settings:', error)
    },
  })
}

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { UserSettingsResponseSchema } from '@/schemas/UserSettingsSchemas'
import { useAuthQuery } from '@/hooks/useAuthQuery'
import type { UserSettingsResponse, UpdateUserSettingsRequest } from '@/types/UserSettingsTypes'

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
  const { data: user } = useAuthQuery()
  const isAuthenticated = !!user

  return useQuery({
    queryKey: userSettingsKeys.settings(),
    queryFn: async (): Promise<UserSettingsResponse> => {
      const data = await ApiClient.get<UserSettingsResponse>('/api/user/settings')
      const result = UserSettingsResponseSchema.safeParse(data)
      if (!result.success) {
        console.error('User settings validation failed:', result.error)
        throw new Error('Invalid user settings data received from server')
      }
      return result.data
    },
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000, // 5 minutes
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
      const result = UserSettingsResponseSchema.safeParse(data)
      if (!result.success) {
        console.error('User settings response validation failed:', result.error)
        throw new Error('Invalid user settings response received from server')
      }
      return result.data
    },
    onSuccess: (settings) => {
      queryClient.setQueryData(userSettingsKeys.settings(), settings)
    },
    onError: (error) => {
      console.error('Failed to update user settings:', error)
    },
  })
}

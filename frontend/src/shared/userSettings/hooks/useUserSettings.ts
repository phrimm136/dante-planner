import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { validateData } from '@/lib/validation'
import { UserSettingsResponseSchema } from '../schemas/UserSettingsSchemas'
import { useAuthQueryNonBlocking } from '@/shared/auth'
import type {
  UserSettingsResponse,
  UpdateUserSettingsRequest,
} from '../schemas/UserSettingsSchemas'
import { STALE_TIME } from '@/lib/constants'

export const userSettingsKeys = {
  settings: () => ['user', 'settings'] as const,
}

export function useUserSettingsQuery() {
  const { data: user } = useAuthQueryNonBlocking()
  const isAuthenticated = !!user

  return useQuery({
    queryKey: userSettingsKeys.settings(),
    queryFn: async ({ signal }): Promise<UserSettingsResponse> => {
      const data = await ApiClient.get<UserSettingsResponse>('/api/user/settings', { signal })
      return validateData(data, UserSettingsResponseSchema, 'user settings')
    },
    enabled: isAuthenticated,
    staleTime: STALE_TIME.MEDIUM,
  })
}

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

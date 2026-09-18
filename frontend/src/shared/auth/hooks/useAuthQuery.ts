import {
  useSuspenseQuery,
  useQuery,
  useMutation,
  useQueryClient,
  queryOptions,
} from '@tanstack/react-query'
import { ApiClient } from '@/lib/api'
import { BackendUnavailableError, ServiceUpdatingError } from '@/lib/apiErrors'
import { queryClient } from '@/lib/queryClient'
import { validateDataOrNull } from '@/lib/validation'
import { UserSchema, type User } from '../schemas/AuthSchemas'
import { STALE_TIME } from '@/lib/constants'

export const authQueryKeys = {
  me: ['auth', 'me'] as const,
}

export function createAuthMeQueryOptions() {
  return queryOptions({
    queryKey: authQueryKeys.me,
    queryFn: async ({ signal }): Promise<User | null> => {
      try {
        const data = await ApiClient.get<User | null>('/api/auth/me', { signal })
        if (data == null) return null
        return validateDataOrNull(data, UserSchema, 'auth me')
      } catch (error) {
        if (
          error instanceof BackendUnavailableError ||
          error instanceof ServiceUpdatingError ||
          error instanceof TypeError
        ) {
          const cached = queryClient.getQueryData<User | null>(authQueryKeys.me)
          if (cached) return cached
        }
        return null
      }
    },
    staleTime: STALE_TIME.MEDIUM,
    retry: false, // Don't retry auth failures
  })
}

export function useAuthQuery() {
  return useSuspenseQuery(createAuthMeQueryOptions())
}

export function useAuthQueryNonBlocking() {
  return useQuery(createAuthMeQueryOptions())
}

export function useLogout() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async () => {
      await ApiClient.post('/api/auth/logout')
    },
    meta: { successMessage: 'common:header.auth.successLogout' },
    onSuccess: () => {
      queryClient.setQueryData(authQueryKeys.me, null)
    },
    onError: (error) => {
      console.error('Logout failed:', error)
    },
  })
}

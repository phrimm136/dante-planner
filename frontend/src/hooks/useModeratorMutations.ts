import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiClient } from '@/lib/api'
import { moderatorQueryKeys } from './useModeratorData'

interface BanUserRequest {
  usernameSuffix: string
  reason?: string
}

interface TimeoutUserRequest {
  usernameSuffix: string
  durationMinutes: number
  reason: string
}

interface UnbanUserRequest {
  usernameSuffix: string
  reason: string
}

interface UntimeoutUserRequest {
  usernameSuffix: string
  reason: string
}

/**
 * Hook for banning a user
 */
export function useBanUser() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({ usernameSuffix, reason }: BanUserRequest) => {
      await ApiClient.post(`/api/moderation/user/${usernameSuffix}/ban`, { reason })
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: moderatorQueryKeys.users() })
      void queryClient.invalidateQueries({ queryKey: moderatorQueryKeys.actions() })
    },
  })
}

/**
 * Hook for unbanning a user
 */
export function useUnbanUser() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({ usernameSuffix, reason }: UnbanUserRequest) => {
      await ApiClient.post(`/api/moderation/user/${usernameSuffix}/unban`, { reason })
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: moderatorQueryKeys.users() })
      void queryClient.invalidateQueries({ queryKey: moderatorQueryKeys.actions() })
    },
  })
}

/**
 * Hook for timing out a user
 */
export function useTimeoutUser() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({ usernameSuffix, durationMinutes, reason }: TimeoutUserRequest) => {
      await ApiClient.post(`/api/moderation/user/${usernameSuffix}/timeout`, { durationMinutes, reason })
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: moderatorQueryKeys.users() })
      void queryClient.invalidateQueries({ queryKey: moderatorQueryKeys.actions() })
    },
  })
}

/**
 * Hook for removing timeout from a user
 */
export function useUntimeoutUser() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({ usernameSuffix, reason }: UntimeoutUserRequest) => {
      await ApiClient.post(`/api/moderation/user/${usernameSuffix}/clear-timeout`, { reason })
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: moderatorQueryKeys.users() })
      void queryClient.invalidateQueries({ queryKey: moderatorQueryKeys.actions() })
    },
  })
}

import { useSuspenseQuery, useMutation, useQueryClient, queryOptions } from '@tanstack/react-query'
import { ApiClient } from '@/lib/api'
import { EpithetListResponseSchema, UserDeletionResponseSchema } from '@/schemas/UserSettingsSchemas'
import { UserSchema } from '@/schemas/AuthSchemas'
import { authQueryKeys } from '@/hooks/useAuthQuery'
import type { EpithetListResponse, UpdateUsernameEpithetRequest, UserDeletionResponse } from '@/types/UserSettingsTypes'
import type { User } from '@/schemas/AuthSchemas'

/**
 * Query keys for user settings queries
 */
export const userSettingsQueryKeys = {
  epithets: () => ['user', 'epithets'] as const,
}

/**
 * Query options for fetching all available epithets.
 * This is a public endpoint - no auth required.
 */
function createEpithetsQueryOptions() {
  return queryOptions({
    queryKey: userSettingsQueryKeys.epithets(),
    queryFn: async (): Promise<EpithetListResponse> => {
      const data = await ApiClient.get<EpithetListResponse>('/api/user/epithets')
      const result = EpithetListResponseSchema.safeParse(data)
      if (!result.success) {
        console.error('Epithets validation failed:', result.error)
        throw new Error('Invalid epithets data received from server')
      }
      return result.data
    },
    staleTime: 24 * 60 * 60 * 1000, // 24 hours - epithets rarely change
  })
}

/**
 * Hook to fetch all available username epithets.
 * Uses Suspense for SSR-compatible loading states.
 *
 * @example
 * ```tsx
 * function UsernameDropdown() {
 *   const { epithets } = useEpithetsQuery();
 *
 *   return (
 *     <select>
 *       {epithets.map(epithet => (
 *         <option key={epithet} value={epithet}>
 *           {t(epithet, { ns: 'epithet' })}
 *         </option>
 *       ))}
 *     </select>
 *   );
 * }
 * ```
 */
export function useEpithetsQuery() {
  const { data } = useSuspenseQuery(createEpithetsQueryOptions())
  return { epithets: data.epithets }
}

/**
 * Hook for updating username epithet mutation.
 * Invalidates auth cache on success so Header updates with new username.
 *
 * @example
 * ```tsx
 * function SaveButton({ epithet }: { epithet: string }) {
 *   const updateEpithet = useUpdateEpithetMutation();
 *
 *   return (
 *     <button
 *       onClick={() => updateEpithet.mutate({ epithet })}
 *       disabled={updateEpithet.isPending}
 *     >
 *       Save
 *     </button>
 *   );
 * }
 * ```
 */
export function useUpdateEpithetMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (request: UpdateUsernameEpithetRequest): Promise<User> => {
      const data = await ApiClient.put<User>('/api/user/me/username-epithet', request)
      const result = UserSchema.safeParse(data)
      if (!result.success) {
        console.error('User validation failed:', result.error)
        throw new Error('Invalid user data received from server')
      }
      return result.data
    },
    onSuccess: (user) => {
      // Update auth cache so Header reflects the new username
      queryClient.setQueryData(authQueryKeys.me, user)
    },
    onError: (error) => {
      console.error('Failed to update username epithet:', error)
    },
  })
}

/**
 * Hook for deleting user account mutation.
 * Invalidates auth cache on success to trigger logout.
 *
 * @example
 * ```tsx
 * function DeleteButton() {
 *   const deleteAccount = useDeleteAccountMutation();
 *
 *   return (
 *     <button
 *       onClick={() => deleteAccount.mutate()}
 *       disabled={deleteAccount.isPending}
 *     >
 *       Delete Account
 *     </button>
 *   );
 * }
 * ```
 */
export function useDeleteAccountMutation() {
  return useMutation({
    mutationFn: async (): Promise<UserDeletionResponse> => {
      const data = await ApiClient.delete<UserDeletionResponse>('/api/user/me')
      const result = UserDeletionResponseSchema.safeParse(data)
      if (!result.success) {
        console.error('User deletion response validation failed:', result.error)
        throw new Error('Invalid user deletion response received from server')
      }
      return result.data
    },
    onError: (error) => {
      console.error('Failed to delete account:', error)
    },
  })
}

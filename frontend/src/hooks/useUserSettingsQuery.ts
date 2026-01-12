import { useSuspenseQuery, useMutation, useQueryClient, queryOptions } from '@tanstack/react-query'
import { ApiClient } from '@/lib/api'
import { AssociationListResponseSchema, UserDeletionResponseSchema } from '@/schemas/UserSettingsSchemas'
import { UserSchema } from '@/schemas/AuthSchemas'
import { authQueryKeys } from '@/hooks/useAuthQuery'
import type { AssociationListResponse, UpdateUsernameKeywordRequest, UserDeletionResponse } from '@/types/UserSettingsTypes'
import type { User } from '@/schemas/AuthSchemas'

/**
 * Query keys for user settings queries
 */
export const userSettingsQueryKeys = {
  associations: () => ['user', 'associations'] as const,
}

/**
 * Query options for fetching all available associations.
 * This is a public endpoint - no auth required.
 */
function createAssociationsQueryOptions() {
  return queryOptions({
    queryKey: userSettingsQueryKeys.associations(),
    queryFn: async (): Promise<AssociationListResponse> => {
      const data = await ApiClient.get<AssociationListResponse>('/api/user/associations')
      const result = AssociationListResponseSchema.safeParse(data)
      if (!result.success) {
        console.error('Associations validation failed:', result.error)
        throw new Error('Invalid associations data received from server')
      }
      return result.data
    },
    staleTime: 24 * 60 * 60 * 1000, // 24 hours - associations rarely change
  })
}

/**
 * Hook to fetch all available username associations.
 * Uses Suspense for SSR-compatible loading states.
 *
 * @example
 * ```tsx
 * function UsernameDropdown() {
 *   const { associations } = useAssociationsQuery();
 *
 *   return (
 *     <select>
 *       {associations.map(a => (
 *         <option key={a.keyword} value={a.keyword}>
 *           {a.displayName}
 *         </option>
 *       ))}
 *     </select>
 *   );
 * }
 * ```
 */
export function useAssociationsQuery() {
  const { data } = useSuspenseQuery(createAssociationsQueryOptions())
  return { associations: data.associations }
}

/**
 * Hook for updating username keyword mutation.
 * Invalidates auth cache on success so Header updates with new username.
 *
 * @example
 * ```tsx
 * function SaveButton({ keyword }: { keyword: string }) {
 *   const updateKeyword = useUpdateKeywordMutation();
 *
 *   return (
 *     <button
 *       onClick={() => updateKeyword.mutate({ keyword })}
 *       disabled={updateKeyword.isPending}
 *     >
 *       Save
 *     </button>
 *   );
 * }
 * ```
 */
export function useUpdateKeywordMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (request: UpdateUsernameKeywordRequest): Promise<User> => {
      const data = await ApiClient.put<User>('/api/user/me/username-keyword', request)
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
      console.error('Failed to update username keyword:', error)
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

/**
 * Moderator Dashboard Data Hooks
 *
 * Fetches user list and moderation action history for moderator dashboard.
 * Uses useSuspenseQuery for consistent loading states with Suspense boundaries.
 *
 * Pattern: useNotificationsQuery.ts
 */

import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { UserForModSchema, ModerationActionSchema } from '@/schemas/ModeratorSchemas'

import type { UserForMod, ModerationAction } from '@/types/ModeratorTypes'

// ============================================================================
// Query Key Factory
// ============================================================================

export const moderatorQueryKeys = {
  all: ['moderator'] as const,
  users: () => ['moderator', 'users'] as const,
  actions: () => ['moderator', 'actions'] as const,
}

// ============================================================================
// Query Options
// ============================================================================

function createModeratorUsersQueryOptions() {
  return queryOptions({
    queryKey: moderatorQueryKeys.users(),
    queryFn: async (): Promise<UserForMod[]> => {
      const data = await ApiClient.get('/api/moderation/users')
      const result = UserForModSchema.array().safeParse(data)

      if (!result.success) {
        console.error('Moderator users validation failed:', result.error)
        throw new Error('Invalid users response from server')
      }

      return result.data
    },
    staleTime: 30 * 1000, // 30 seconds
  })
}

function createModerationHistoryQueryOptions() {
  return queryOptions({
    queryKey: moderatorQueryKeys.actions(),
    queryFn: async (): Promise<ModerationAction[]> => {
      const data = await ApiClient.get('/api/moderation/actions')
      const result = ModerationActionSchema.array().safeParse(data)

      if (!result.success) {
        console.error('Moderation actions validation failed:', result.error)
        throw new Error('Invalid moderation actions response from server')
      }

      return result.data
    },
    staleTime: 10 * 1000, // 10 seconds
  })
}

// ============================================================================
// Hooks
// ============================================================================

/**
 * Hook for fetching all users for moderation dashboard
 *
 * Uses useSuspenseQuery - wrap in Suspense boundary for loading states.
 *
 * @example
 * ```tsx
 * function UserList() {
 *   const users = useModeratorUsers();
 *   return <div>{users.length} users</div>;
 * }
 *
 * // Wrap in Suspense
 * <Suspense fallback={<LoadingSpinner />}>
 *   <UserList />
 * </Suspense>
 * ```
 */
export function useModeratorUsers() {
  const { data } = useSuspenseQuery(createModeratorUsersQueryOptions())
  return data
}

/**
 * Hook for fetching moderation action history
 *
 * Uses useSuspenseQuery - wrap in Suspense boundary for loading states.
 */
export function useModerationHistory() {
  const { data } = useSuspenseQuery(createModerationHistoryQueryOptions())
  return data
}

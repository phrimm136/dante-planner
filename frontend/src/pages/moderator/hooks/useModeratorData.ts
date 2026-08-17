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
import { validateData } from '@/lib/validation'
import { UserForModSchema, ModerationActionSchema } from '../schemas/ModeratorSchemas'

import type { UserForMod, ModerationAction } from '../types/ModeratorTypes'
import { STALE_TIME } from '@/lib/constants'

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

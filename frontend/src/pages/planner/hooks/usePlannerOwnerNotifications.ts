/**
 * Planner Owner Notifications Toggle Hook
 *
 * Allows planner owner to enable/disable comment notifications.
 * Only owners can toggle this setting.
 *
 * Pattern: usePlannerSubscription.ts (toggle mutation + cache invalidation)
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { z } from 'zod'

import { ApiClient } from '@/lib/api'
import { validateData } from '@/lib/validation'
import { publishedPlannerQueryKeys } from './usePublishedPlannerQuery'

interface ToggleOwnerNotificationsInput {
  plannerId: string
  enabled: boolean
}

const ToggleOwnerNotificationsResponseSchema = z
  .object({
    ownerNotificationsEnabled: z.boolean(),
  })
  .strict()

type ToggleOwnerNotificationsResponse = z.infer<typeof ToggleOwnerNotificationsResponseSchema>

/**
 * Hook for toggling owner notification settings on a planner
 *
 * @example
 * ```tsx
 * function OwnerNotificationButton({ planner }) {
 *   const toggle = useToggleOwnerNotifications();
 *
 *   const handleToggle = () => {
 *     toggle.mutate({
 *       plannerId: planner.id,
 *       enabled: !planner.ownerNotificationsEnabled
 *     });
 *   };
 *
 *   return (
 *     <button onClick={handleToggle} disabled={toggle.isPending}>
 *       {planner.ownerNotificationsEnabled ? 'Disable' : 'Enable'}
 *     </button>
 *   );
 * }
 * ```
 */
export function useToggleOwnerNotifications() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({
      plannerId,
      enabled,
    }: ToggleOwnerNotificationsInput): Promise<ToggleOwnerNotificationsResponse> => {
      const data = await ApiClient.patch(`/api/planner/md/${plannerId}/notifications`, {
        enabled,
      })
      return validateData(
        data,
        ToggleOwnerNotificationsResponseSchema,
        'planner owner notifications',
      )
    },
    onSuccess: (_, { plannerId }) => {
      void queryClient.invalidateQueries({
        queryKey: publishedPlannerQueryKeys.detail(plannerId),
      })
    },
    onError: (error) => {
      console.error('Toggle owner notifications failed:', error)
    },
  })
}

/**
 * Planner Subscription Mutation Hook
 *
 * Handles subscribing/unsubscribing to community planners.
 * Toggle endpoint - calling again removes the subscription.
 * Invalidates planner list cache on success.
 *
 * Pattern: usePlannerBookmark.ts (toggle mutation + cache invalidation)
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { SubscriptionResponseSchema } from '@/schemas/PlannerListSchemas'
import { publishedPlannerQueryKeys } from './usePublishedPlannerQuery'

import type { SubscriptionResponse } from '@/types/PlannerListTypes'

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook for subscribing to community planners
 *
 * @example
 * ```tsx
 * function PlannerCard({ planner }) {
 *   const subscription = usePlannerSubscription();
 *
 *   const handleSubscribe = () => {
 *     subscription.mutate(planner.id);
 *   };
 *
 *   return (
 *     <button
 *       onClick={handleSubscribe}
 *       disabled={subscription.isPending}
 *       aria-pressed={planner.isSubscribed}
 *     >
 *       {planner.isSubscribed ? 'Subscribed' : 'Subscribe'}
 *     </button>
 *   );
 * }
 * ```
 */
export function usePlannerSubscription() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (plannerId: string): Promise<SubscriptionResponse> => {
      const data = await ApiClient.post(`/api/planner/md/${plannerId}/subscribe`)
      const result = SubscriptionResponseSchema.safeParse(data)

      if (!result.success) {
        console.error('Subscription response validation failed:', result.error)
        throw new Error('Invalid subscription response from server')
      }

      return result.data
    },
    onSuccess: (_data, plannerId) => {
      // Invalidate only the specific planner detail query
      // Subscription state is not displayed in list view, so no need to invalidate list
      void queryClient.invalidateQueries({ queryKey: publishedPlannerQueryKeys.detail(plannerId) })
    },
    onError: (error) => {
      console.error('Subscription failed:', error)
    },
  })
}

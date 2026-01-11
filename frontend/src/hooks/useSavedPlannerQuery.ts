import { useSuspenseQuery } from '@tanstack/react-query'
import { usePlannerStorage } from './usePlannerStorage'
import type { SaveablePlanner } from '@/types/PlannerTypes'

/**
 * Query key factory for planner queries
 */
export const plannerQueryKeys = {
  detail: (id: string) => ['planner', id] as const,
}

/**
 * Hook to load a saved planner by ID using Suspense
 *
 * @param plannerId - The planner ID to load
 * @returns The loaded planner data, or null if not found
 *
 * @example
 * ```tsx
 * function PlannerDetailPage() {
 *   const { id } = useParams()
 *   const planner = useSavedPlannerQuery(id)
 *
 *   if (!planner) {
 *     return <NotFound />
 *   }
 *
 *   return <PlannerViewer planner={planner} />
 * }
 * ```
 */
export function useSavedPlannerQuery(plannerId: string): SaveablePlanner | null {
  const { loadPlanner } = usePlannerStorage()

  const query = useSuspenseQuery({
    queryKey: plannerQueryKeys.detail(plannerId),
    queryFn: () => loadPlanner(plannerId),
    staleTime: 0, // Always refetch from IndexedDB to get latest version
    gcTime: 5 * 60 * 1000, // Keep in cache for 5 minutes
  })

  return query.data
}

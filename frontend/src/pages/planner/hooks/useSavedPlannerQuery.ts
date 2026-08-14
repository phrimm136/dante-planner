import { useSuspenseQuery } from '@tanstack/react-query'
import { usePlannerStorage } from './usePlannerStorage'
import { plannerQueryKeys } from '../lib/plannerQueryKeys'
import type { SaveablePlanner } from '../types/PlannerTypes'
import { GC_TIME } from '@/lib/constants'

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
  const { loadFromLocal } = usePlannerStorage()

  const query = useSuspenseQuery({
    queryKey: plannerQueryKeys.detail(plannerId),
    queryFn: async () => {
      const result = await loadFromLocal(plannerId)
      if (!result.ok) {
        throw new Error(`Failed to load planner ${plannerId}: ${result.error}`)
      }
      return result.value
    },
    staleTime: 0, // Always refetch from IndexedDB to get latest version
    gcTime: GC_TIME.SHORT,
    // The source is local storage, so regaining focus says nothing about it.
    refetchOnWindowFocus: false,
  })

  return query.data
}

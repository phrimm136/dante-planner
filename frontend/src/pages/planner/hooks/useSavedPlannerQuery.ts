import { useSuspenseQuery } from '@tanstack/react-query'
import { usePlannerStorage } from './usePlannerStorage'
import { plannerQueryKeys } from '../lib/plannerQueryKeys'
import type { SaveablePlanner } from '../types/PlannerTypes'
import { GC_TIME } from '@/lib/constants'

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
    refetchOnWindowFocus: false,
  })

  return query.data
}

import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import type { EGOGiftObservationData } from '@/types/EGOGiftObservationTypes'
import { EGOGiftObservationDataSchema } from '@/schemas'

// Query key factory for observation data
export const egoGiftObservationQueryKeys = {
  all: ['egoGiftObservation', 'md6'] as const,
}

// Observation data query options
function createObservationDataQueryOptions() {
  return queryOptions({
    queryKey: egoGiftObservationQueryKeys.all,
    queryFn: async () => {
      const module = await import('@static/data/MD6/egoGiftObservationData.json')
      const result = EGOGiftObservationDataSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[egoGiftObservation] Validation failed: ${result.error.message}`)
      }
      return result.data as EGOGiftObservationData
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads EGO gift observation data for MD6
 * Suspends while loading - wrap in Suspense boundary
 * @returns observation pool (cost data + eligible gift IDs)
 */
export function useEGOGiftObservationData() {
  const { data } = useSuspenseQuery(createObservationDataQueryOptions())
  return { data }
}

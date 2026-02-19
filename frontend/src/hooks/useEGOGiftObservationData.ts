import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import type { EGOGiftObservationData } from '@/types/EGOGiftObservationTypes'
import { EGOGiftObservationDataSchema } from '@/schemas'

// Query key factory for observation data
export const egoGiftObservationQueryKeys = {
  all: (version: number) => ['egoGiftObservation', `md${version}`] as const,
}

// Observation data query options
function createObservationDataQueryOptions(version: number) {
  return queryOptions({
    queryKey: egoGiftObservationQueryKeys.all(version),
    queryFn: async () => {
      const module = await import(`@static/data/MD${version}/egoGiftObservationData.json`)
      const result = EGOGiftObservationDataSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[egoGiftObservation/md${version}] Validation failed: ${result.error.message}`)
      }
      return result.data as EGOGiftObservationData
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads EGO gift observation data for a specific MD version
 * Suspends while loading - wrap in Suspense boundary
 * @param version - Mirror Dungeon version
 * @returns observation pool (cost data + eligible gift IDs)
 */
export function useEGOGiftObservationData(version: number) {
  const { data } = useSuspenseQuery(createObservationDataQueryOptions(version))
  return { data }
}

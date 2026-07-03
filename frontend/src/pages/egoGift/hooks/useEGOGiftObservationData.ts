import { useSuspenseQuery } from '@tanstack/react-query'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { EGOGiftObservationDataSchema } from '../schemas/EGOGiftObservationSchemas'

// Versioned key deviates from the standard entity shapes — kept hand-rolled
export const egoGiftObservationQueryKeys = {
  all: (version: number) => ['egoGiftObservation', `md${version}`] as const,
}

function createObservationDataQueryOptions(version: number) {
  return createStaticDataQueryOptions(
    egoGiftObservationQueryKeys.all(version),
    () => import(`@static/data/MD${version}/egoGiftObservationData.json`),
    EGOGiftObservationDataSchema,
    `egoGiftObservation/md${version}`,
  )
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

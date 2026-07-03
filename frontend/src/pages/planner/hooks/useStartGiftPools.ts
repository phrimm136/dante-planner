import { useSuspenseQuery } from '@tanstack/react-query'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import type { MDVersion } from '@/shared/gameData'
import type { StartEgoGiftPools } from '../types/StartGiftTypes'
import { StartEgoGiftPoolsSchema } from '../schemas/StartGiftSchemas'

// Query key factory for start gift pools
// Hand-rolled: versioned tuple deviates from the shared list/detail factory shapes
export const startGiftPoolsQueryKeys = {
  all: (version: MDVersion) => ['startGiftPools', `md${version}`] as const,
}

function createPoolsQueryOptions(version: MDVersion) {
  return createStaticDataQueryOptions(
    startGiftPoolsQueryKeys.all(version),
    () => import(`@static/data/MD${version}/startEgoGiftPools.json`),
    StartEgoGiftPoolsSchema,
    `startGiftPools/md${version}`,
  )
}

/**
 * Hook that loads start gift pool data for a specific MD version
 * Suspends while loading - wrap in Suspense boundary
 * @param version - Mirror Dungeon version (5 or 6)
 * @returns keyword -> gift IDs mapping
 */
export function useStartGiftPools(version: MDVersion): { data: StartEgoGiftPools } {
  const { data } = useSuspenseQuery(createPoolsQueryOptions(version))
  return { data }
}

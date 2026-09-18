import { useSuspenseQuery } from '@tanstack/react-query'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import type { MDVersion } from '@/shared/gameData'
import type { StartEgoGiftPools } from '../types/StartGiftTypes'
import { StartEgoGiftPoolsSchema } from '../schemas/StartGiftSchemas'

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

export function useStartGiftPools(version: MDVersion): { data: StartEgoGiftPools } {
  const { data } = useSuspenseQuery(createPoolsQueryOptions(version))
  return { data }
}

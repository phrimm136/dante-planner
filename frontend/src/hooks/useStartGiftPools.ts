import { useQuery, queryOptions } from '@tanstack/react-query'
import type { MDVersion } from '@/hooks/useStartBuffData'
import type { StartEgoGiftPools } from '@/types/StartGiftTypes'
import { StartEgoGiftPoolsSchema } from '@/schemas'

// Query key factory for start gift pools
export const startGiftPoolsQueryKeys = {
  all: (version: MDVersion) => ['startGiftPools', `md${version}`] as const,
}

// Pool data query options
function createPoolsQueryOptions(version: MDVersion) {
  return queryOptions({
    queryKey: startGiftPoolsQueryKeys.all(version),
    queryFn: async () => {
      const module = await import(`@static/data/MD${version}/startEgoGiftPools.json`)
      const result = StartEgoGiftPoolsSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[startGiftPools/md${version}] Validation failed: ${result.error.message}`)
      }
      return result.data as StartEgoGiftPools
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads start gift pool data for a specific MD version
 * @param version - Mirror Dungeon version (5 or 6)
 * @returns keyword -> gift IDs mapping
 */
export function useStartGiftPools(version: MDVersion) {
  const query = useQuery(createPoolsQueryOptions(version))

  return {
    data: query.data,
    isPending: query.isPending,
    isError: query.isError,
    error: query.error,
  }
}

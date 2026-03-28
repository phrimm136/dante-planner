import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { AbEventSpecListSchema } from '@/schemas'

// Query key factory for AbEvent list data
export const abEventListQueryKeys = {
  all: () => ['abEvent', 'list'] as const,
  spec: () => ['abEvent', 'list', 'spec'] as const,
}

// AbEvent spec list query options with runtime validation
function createAbEventSpecListQueryOptions() {
  return queryOptions({
    queryKey: abEventListQueryKeys.spec(),
    queryFn: async () => {
      const module = await import('@static/data/abEventSpecList.json')
      const result = AbEventSpecListSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[abEvent specList] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads AbEvent spec list (no language dependency)
 *
 * @returns Validated AbEvent spec list map
 */
export function useAbEventListSpec() {
  const { data: spec } = useSuspenseQuery(createAbEventSpecListQueryOptions())
  return spec
}

/**
 * Hook that loads AbEvent list data (spec list only)
 * Suspends while loading - wrap in Suspense boundary
 *
 * @returns Validated AbEvent spec map
 */
export function useAbEventListData() {
  const { data: spec } = useSuspenseQuery(createAbEventSpecListQueryOptions())

  return { spec }
}

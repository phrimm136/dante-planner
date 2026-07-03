import { useSuspenseQuery } from '@tanstack/react-query'
import { createEntityListQueryKeys } from '@/lib/queryKeys'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { AbEventSpecListSchema } from '../schemas/AbEventSchemas'

const listKeys = createEntityListQueryKeys('abEvent')

// abEvent has no list i18n file — expose only the members that have data
export const abEventListQueryKeys = {
  all: listKeys.all,
  spec: listKeys.spec,
}

function createAbEventSpecListQueryOptions() {
  return createStaticDataQueryOptions(
    abEventListQueryKeys.spec(),
    () => import('@static/data/abEventSpecList.json'),
    AbEventSpecListSchema,
    'abEvent specList',
  )
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

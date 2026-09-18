import { useSuspenseQuery } from '@tanstack/react-query'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { EGOGiftObservationDataSchema } from '../schemas/EGOGiftObservationSchemas'

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

export function useEGOGiftObservationData(version: number) {
  const { data } = useSuspenseQuery(createObservationDataQueryOptions(version))
  return { data }
}

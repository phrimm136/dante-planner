import { useSuspenseQuery } from '@tanstack/react-query'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { ColorCodeMapSchema } from '../schemas/ColorCodeSchemas'

export const colorCodeQueryKeys = {
  all: () => ['colorCode'] as const,
}

export function createColorCodeQueryOptions() {
  return createStaticDataQueryOptions(
    colorCodeQueryKeys.all(),
    () => import('@static/data/color/skillDescColorCode.json'),
    ColorCodeMapSchema,
    'colorCode',
  )
}

export function useColorCodes() {
  const { data } = useSuspenseQuery(createColorCodeQueryOptions())
  return { data }
}

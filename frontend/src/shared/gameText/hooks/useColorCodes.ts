import { useSuspenseQuery } from '@tanstack/react-query'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { ColorCodeMapSchema } from '../schemas/ColorCodeSchemas'

// Query key for color codes
// Hand-rolled: single-key namespace, no list/detail domain
export const colorCodeQueryKeys = {
  all: () => ['colorCode'] as const,
}

function createColorCodeQueryOptions() {
  return createStaticDataQueryOptions(
    colorCodeQueryKeys.all(),
    () => import('@static/data/color/skillDescColorCode.json'),
    ColorCodeMapSchema,
    'colorCode',
  )
}

/**
 * Hook that loads color code mapping data
 * Suspends while loading - wrap in Suspense boundary
 * @returns keyword id -> hex color mapping
 */
export function useColorCodes() {
  const { data } = useSuspenseQuery(createColorCodeQueryOptions())
  return { data }
}

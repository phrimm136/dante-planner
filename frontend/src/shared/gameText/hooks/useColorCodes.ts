import { useSuspenseQuery } from '@tanstack/react-query'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import type { ColorCodeMap } from '../types/ColorCodeTypes'
import { ColorCodeMapSchema } from '../schemas/ColorCodeSchemas'

// Query key for color codes
// Hand-rolled: single-key namespace, no list/detail domain
export const colorCodeQueryKeys = {
  all: () => ['colorCode'] as const,
}

function createColorCodeQueryOptions() {
  return createStaticDataQueryOptions(
    colorCodeQueryKeys.all(),
    () => import('@static/data/colorCode.json'),
    ColorCodeMapSchema,
    'colorCode',
  )
}

/**
 * Hook that loads color code mapping data
 * Suspends while loading - wrap in Suspense boundary
 * @returns attributeType -> hex color mapping
 */
export function useColorCodes() {
  const { data } = useSuspenseQuery(createColorCodeQueryOptions())
  return { data }
}

/**
 * Gets hex color for an attributeType from color code map
 * @param colorCodes - Color code map
 * @param attributeType - Attribute type (e.g., "CRIMSON", "SCARLET")
 * @returns Hex color string or default white
 */
export function getColorForAttributeType(
  colorCodes: ColorCodeMap | undefined,
  attributeType: string
): string {
  return colorCodes?.[attributeType] || '#ffffff'
}

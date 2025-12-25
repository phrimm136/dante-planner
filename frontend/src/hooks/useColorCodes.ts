import { useQuery, queryOptions } from '@tanstack/react-query'
import type { ColorCodeMap } from '@/types/ColorCodeTypes'
import { ColorCodeMapSchema } from '@/schemas'

// Query key for color codes
export const colorCodeQueryKeys = {
  all: () => ['colorCode'] as const,
}

// Color code query options
function createColorCodeQueryOptions() {
  return queryOptions({
    queryKey: colorCodeQueryKeys.all(),
    queryFn: async () => {
      const module = await import('@static/data/colorCode.json')
      const result = ColorCodeMapSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[colorCode] Validation failed: ${result.error.message}`)
      }
      return result.data as ColorCodeMap
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads color code mapping data
 * @returns attributeType -> hex color mapping
 */
export function useColorCodes() {
  const query = useQuery(createColorCodeQueryOptions())

  return {
    data: query.data,
    isPending: query.isPending,
    isError: query.isError,
    error: query.error,
  }
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

import colorCode from '@static/data/colorCode.json'
import { darkenColor } from '@/lib/colorUtils'

/**
 * Color pair for gradient styling
 */
export interface AttributeColors {
  /** Primary attribute color (for stripes) */
  primary: string
  /** Darkened color (for background gradient) */
  dark: string
}

/** Fallback colors for unknown/missing attribute types */
const FALLBACK_COLORS: AttributeColors = {
  primary: '#888888',
  dark: '#444444',
}

/**
 * Gets color pair for an attribute type with case-insensitive lookup
 * @param attributeType - Attribute type (e.g., "AZURE", "azure", "Azure")
 * @returns Color pair with primary and dark variants
 */
export function getAttributeColors(attributeType?: string): AttributeColors {
  if (!attributeType) {
    return FALLBACK_COLORS
  }

  // Case-insensitive lookup: try original, uppercase, then title case
  const colorMap = colorCode as Record<string, string>
  const normalized = attributeType.toUpperCase()

  // Try uppercase first (most attribute types are uppercase)
  let primary = colorMap[normalized]

  // If not found, try title case (e.g., "Neutral")
  if (!primary) {
    const titleCase = normalized.charAt(0) + normalized.slice(1).toLowerCase()
    primary = colorMap[titleCase]
  }

  // If still not found, try original
  if (!primary) {
    primary = colorMap[attributeType]
  }

  // If no match, return fallback
  if (!primary) {
    return FALLBACK_COLORS
  }

  return {
    primary,
    dark: darkenColor(primary, 0.5),
  }
}

/**
 * Season color mapping
 * 0 = standard (no color)
 */
const SEASON_COLORS_MAP: Record<number, string> = {
  1: '#920000',
  2: '#d3e3ea',
  3: '#26babe',
  4: '#714d95',
  5: '#f8e925',
  6: '#51dcbd',
  7: '#ce1c18',
  8000: '#a1bece',
} as const

/**
 * Get color for a season code
 * - Exact matches: 1-7, 8000
 * - Range: 9100-9199 → #85e800
 * - Returns undefined for 0 (standard) or unknown codes
 */
export function getSeasonColor(code: number): string | undefined {
  if (code >= 9100 && code <= 9199) return '#85e800'
  return SEASON_COLORS_MAP[code]
}

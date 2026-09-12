import attributeColorCode from '@static/data/color/attributeColorCode.json'
import seasonColorCode from '@static/data/color/seasonColorCode.json'
import { darkenColor } from '@/lib/colorUtils'
import type { HexColor } from '@/lib/colorUtils'
import { WALPURGIS_SEASON_CODE_MAX, WALPURGIS_SEASON_CODE_MIN } from './constants'
import { AttributeColorCodeSchema, SeasonColorCodeSchema } from './schemas/ColorSchemas'
import type { AttributeColorRoles } from './schemas/ColorSchemas'

/**
 * Color pair for gradient styling
 */
export interface AttributeColors {
  /** The attribute's type color (plates, tab fills, name text) */
  primary: HexColor
  /** The floor of the skill name plate's right-hand ramp */
  dark: HexColor
}

/** Fallback colors for unknown/missing attribute types */
const FALLBACK_COLORS: AttributeColors = {
  primary: '#888888',
  dark: '#444444',
}

const ATTRIBUTE_ROLES: Partial<Record<string, AttributeColorRoles>> =
  AttributeColorCodeSchema.parse(attributeColorCode)

/** Brightness the skill name plate sprite ramps down to at its right end */
const PLATE_RAMP_FLOOR = 0.33

const SEASON_COLORS = SeasonColorCodeSchema.parse(seasonColorCode)

/**
 * Gets color pair for an attribute type
 * @param attributeType - Attribute type as the client enum spells it (e.g., "AZURE", "NEUTRAL")
 * @returns Type color and the plate ramp floor it darkens to
 */
export function getAttributeColors(attributeType?: string): AttributeColors {
  const primary = attributeType ? ATTRIBUTE_ROLES[attributeType]?.type : undefined
  if (!primary) {
    return FALLBACK_COLORS
  }
  return {
    primary,
    dark: darkenColor(primary, 1 - PLATE_RAMP_FLOOR),
  }
}

/**
 * Get color for a season code
 * - Walpurgisnacht codes share one entry
 * - Returns undefined for 0 (standard) or unknown codes
 */
export function getSeasonColor(code: number): HexColor | undefined {
  const key =
    code >= WALPURGIS_SEASON_CODE_MIN && code <= WALPURGIS_SEASON_CODE_MAX
      ? String(WALPURGIS_SEASON_CODE_MIN)
      : String(code)
  return SEASON_COLORS[key]
}

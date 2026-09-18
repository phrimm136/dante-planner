import attributeColorCode from '@static/data/color/attributeColorCode.json'
import seasonColorCode from '@static/data/color/seasonColorCode.json'
import { darkenColor } from '@/lib/colorUtils'
import type { HexColor } from '@/lib/colorUtils'
import { WALPURGIS_SEASON_CODE_MAX, WALPURGIS_SEASON_CODE_MIN } from './constants'
import { AttributeColorCodeSchema, SeasonColorCodeSchema } from './schemas/ColorSchemas'
import type { AttributeColorRoles } from './schemas/ColorSchemas'

export interface AttributeColors {
  primary: HexColor
  dark: HexColor
}

const FALLBACK_COLORS: AttributeColors = {
  primary: '#888888',
  dark: '#444444',
}

const ATTRIBUTE_ROLES: Partial<Record<string, AttributeColorRoles>> =
  AttributeColorCodeSchema.parse(attributeColorCode)

const PLATE_RAMP_FLOOR = 0.33

const SEASON_COLORS = SeasonColorCodeSchema.parse(seasonColorCode)

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

export function getSeasonColor(code: number): HexColor | undefined {
  const key =
    code >= WALPURGIS_SEASON_CODE_MIN && code <= WALPURGIS_SEASON_CODE_MAX
      ? String(WALPURGIS_SEASON_CODE_MIN)
      : String(code)
  return SEASON_COLORS[key]
}

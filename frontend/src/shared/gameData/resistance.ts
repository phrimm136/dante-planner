/**
 * Resistance category key type (used as i18n translation key)
 * Maps to: t(`identity.resist.${categoryKey}`)
 */
export type ResistanceCategoryKey = 'fatal' | 'weak' | 'normal' | 'endure' | 'ineffective'

export interface ResistanceInfo {
  categoryKey: ResistanceCategoryKey
  value: number
  color: string
}

/**
 * Resistance bands ordered from most to least damage taken. The first band
 * whose predicate accepts the value wins; anything below every band (and NaN)
 * falls through to `RESISTANCE_FALLBACK_BAND`.
 */
const RESISTANCE_BANDS: ReadonlyArray<{
  categoryKey: ResistanceCategoryKey
  color: string
  matches: (value: number) => boolean
}> = [
  { categoryKey: 'fatal', color: 'text-red-500', matches: (value) => value > 1.5 },
  { categoryKey: 'weak', color: 'text-orange-300', matches: (value) => value > 1.0 },
  { categoryKey: 'normal', color: 'text-amber-100', matches: (value) => value === 1.0 },
  { categoryKey: 'endure', color: 'text-gray-400', matches: (value) => value >= 0.75 },
]

const RESISTANCE_FALLBACK_BAND = {
  categoryKey: 'ineffective' as ResistanceCategoryKey,
  color: 'text-gray-500',
}

/**
 * Gets resistance category key and color based on resistance value
 * Use categoryKey with t(`identity.resist.${categoryKey}`) for localized display
 */
export function getResistanceInfo(value: number): ResistanceInfo {
  const band = RESISTANCE_BANDS.find((b) => b.matches(value)) ?? RESISTANCE_FALLBACK_BAND
  return { categoryKey: band.categoryKey, value, color: band.color }
}

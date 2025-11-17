/**
 * Removes bracket notation from strings used in game data
 * Example: "[yiSang]" -> "yiSang", "[rupture]" -> "rupture"
 */
export function parseBracketNotation(valueWithBrackets: string): string {
  return valueWithBrackets.replace(/[[\]]/g, '')
}

/**
 * Gets the image path for an identity card
 */
export function getIdentityImagePath(identityId: string): string {
  return `/images/identity/${identityId}/gacksung_info.webp`
}

/**
 * Gets the image path for an uptie frame based on star rating
 */
export function getUptieFramePath(star: number): string {
  return `/images/formation/${star}Star4UptieFrame.webp`
}

/**
 * Gets the image path for sinner background based on star rating
 */
export function getSinnerBGPath(star: number): string {
  return `/images/formation/${star}StarSinnerBG.webp`
}

/**
 * Gets the image path for a sinner icon
 */
export function getSinnerIconPath(sinner: string): string {
  const sinnerName = parseBracketNotation(sinner)
  return `/images/sinners/${sinnerName}.webp`
}

/**
 * Gets the image path for a status effect icon
 */
export function getStatusEffectIconPath(keyword: string): string {
  const effectName = parseBracketNotation(keyword)
  return `/images/statusEffect/${effectName}.webp`
}

/**
 * Resistance category type
 */
export type ResistanceCategory = 'Fatal' | 'Weak' | 'Normal' | 'Endure' | 'Ineff.'

/**
 * Resistance info with category and color
 */
export interface ResistanceInfo {
  category: ResistanceCategory
  value: number
  color: string
}

/**
 * Gets resistance category and color based on resistance value
 */
export function getResistanceInfo(value: number): ResistanceInfo {
  if (value > 1.5 && value <= 2) {
    return { category: 'Fatal', value, color: 'text-red-500' }
  } else if (value > 1.0 && value <= 1.5) {
    return { category: 'Weak', value, color: 'text-orange-300' }
  } else if (value === 1.0) {
    return { category: 'Normal', value, color: 'text-amber-100' }
  } else if (value >= 0.75 && value < 1.0) {
    return { category: 'Endure', value, color: 'text-gray-400' }
  } else {
    return { category: 'Ineff.', value, color: 'text-gray-500' }
  }
}

/**
 * Calculates stagger threshold HP value
 */
export function calculateStaggerThreshold(maxHP: number, staggerPercent: number): number {
  return Math.floor(maxHP * staggerPercent)
}

/**
 * Gets rarity icon path based on grade
 */
export function getRarityIconPath(grade: number): string {
  return `/images/UI/identity/rarity${grade}.webp`
}

/**
 * Gets identity detail image path with variant support
 */
export function getIdentityDetailImagePath(
  identityId: string,
  variant: 'gacksung' | 'normal' = 'gacksung'
): string {
  return `/images/identity/${identityId}/${variant}.webp`
}

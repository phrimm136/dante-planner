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
  return `/images/identity/${identityId}/gacksung_info.png`
}

/**
 * Gets the image path for an uptie frame based on star rating
 */
export function getUptieFramePath(star: number): string {
  return `/images/formation/${star}Star4UptieFrame.png`
}

/**
 * Gets the image path for sinner background based on star rating
 */
export function getSinnerBGPath(star: number): string {
  return `/images/formation/${star}StarSinnerBG.png`
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

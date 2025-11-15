/**
 * Converts sinner name from bracket notation to camelCase for image paths
 * Example: "[yiSang]" -> "yiSang"
 */
export function parseSinnerName(sinnerWithBrackets: string): string {
  return sinnerWithBrackets.replace(/[[\]]/g, '')
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
  const sinnerName = parseSinnerName(sinner)
  return `/images/sinners/${sinnerName}.webp`
}

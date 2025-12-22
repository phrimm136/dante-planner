import { SINS } from './constants'

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
  return `/images/UI/formation/${star}Star4UptieFrame.webp`
}

/**
 * Gets the image path for sinner background based on star rating
 */
export function getSinnerBGPath(star: number): string {
  return `/images/UI/formation/${star}StarSinnerBG.webp`
}

/**
 * Gets the image path for a sinner icon
 * Expects PascalCase sinner name (e.g., "YiSang")
 */
export function getSinnerIconPath(sinner: string): string {
  return `/images/icon/sinners/${sinner}.webp`
}

/**
 * Gets the image path for a sin icon
 * Expects PascalCase sin name (e.g., "Wrath", "Lust")
 */
export function getSinIconPath(sin: string): string {
  return `/images/icon/sin/${sin}.webp`
}

/**
 * Gets the image path for a status effect icon
 * Expects PascalCase keyword (e.g., "Rupture")
 */
export function getStatusEffectIconPath(keyword: string): string {
  return `/images/icon/statusEffect/${keyword}.webp`
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

/**
 * Gets skill image path with variant and uptie support
 * @param identityId - Identity ID
 * @param skillSlot - Skill slot (1-3 or 4 for defense)
 * @param variantIndex - Variant index (0 for first variant, 1+ for additional variants)
 * @param isUptie4 - Whether to use uptie4 image (with _4 postfix)
 * @returns Image path
 */
export function getSkillImagePath(
  identityId: string,
  skillSlot: number,
  variantIndex: number = 0,
  isUptie4: boolean = false
): string {
  const slotNum = String(skillSlot).padStart(2, '0')
  const variantSuffix = variantIndex > 0 ? `-${variantIndex + 1}` : ''
  const uptieSuffix = isUptie4 ? '_4' : ''

  return `/images/identity/${identityId}/skill${slotNum}${variantSuffix}${uptieSuffix}.webp`
}

/**
 * Gets sin frame image path based on sin type and skill slot
 * @param sin - Sin type (lowercase) or undefined for defense skills without sin
 * @param skillSlot - Skill slot (1-3 for offensive, 4 for defense)
 * @returns Frame image path
 */
export function getSinFramePath(sin: string | undefined, skillSlot: number): string {
  // Defense skills without sin use defense1
  if (!sin) {
    return `/images/UI/skillFrame/defense1.webp`
  }

  // skill1 -> sin1, skill2 -> sin2, skill3 -> sin3, skillDef with sin -> sin1
  const frameLevel = skillSlot <= 3 ? skillSlot : 1
  return `/images/UI/skillFrame/${sin}${frameLevel}.webp`
}

/**
 * Gets sin frame background image path
 * @param sin - Sin type (lowercase) or undefined for defense skills without sin
 * @param skillSlot - Skill slot (1-3 for offensive, 4 for defense)
 * @returns Frame background image path
 */
export function getSinFrameBGPath(sin: string | undefined, skillSlot: number): string {
  if (!sin) {
    return `/images/UI/skillFrame/defense1BG.webp`
  }

  const frameLevel = skillSlot <= 3 ? skillSlot : 1
  return `/images/UI/skillFrame/${sin}${frameLevel}BG.webp`
}

/**
 * Gets attack type icon path
 * @param atkType - Attack type (slash, pierce, blunt, attack)
 * @returns Icon path
 */
export function getAttackTypeIconPath(atkType: string): string {
  return `/images/UI/identity/${atkType}.webp`
}

/**
 * Gets attack type frame path
 * @param sin - Sin type in PascalCase (e.g., "Wrath")
 * @returns Attack type frame path
 */
export function getAttackTypeFramePath(sin: string): string {
  return `/images/UI/skillFrame/attackType${sin}.webp`
}

/**
 * Gets attack type frame background path
 * @param sin - Sin type in PascalCase (e.g., "Wrath")
 * @returns Attack type frame background path
 */
export function getAttackTypeFrameBGPath(sin: string): string {
  return `/images/UI/skillFrame/attackTypeBG${sin}.webp`
}

/**
 * Gets coin icon path based on coin type (for EA display)
 * @param coinType - Coin type ('C' for normal, 'U' for unbreakable)
 * @returns Coin icon path
 */
export function getCoinIconPath(coinType: 'C' | 'U'): string {
  const iconName = coinType === 'U' ? 'unbreakableCoin' : 'coin'
  return `/images/icon/${iconName}.webp`
}

/**
 * Gets coin icon path for description text (numbered coins)
 * @param coinIndex - Coin index (0-based, will be converted to 1-based)
 * @returns Coin icon path for descriptions
 */
export function getCoinDescIconPath(coinIndex: number): string {
  return `/images/UI/common/coin${coinIndex + 1}.webp`
}

/**
 * EGO-specific utility functions
 */

/**
 * Gets EGO image path (circular awakening image)
 * @param egoId - EGO ID (e.g., "20101")
 * @returns EGO image path
 */
export function getEGOImagePath(egoId: string): string {
  return `/images/EGO/${egoId}/cg.webp`
}

/**
 * Gets EGO frame path (static frame overlay)
 * @returns EGO frame path
 */
export function getEGOFramePath(): string {
  return `/images/UI/formation/egoFrame.webp`
}

/**
 * Gets EGO rank icon path (large rank indicator)
 * @param rank - EGO rank in PascalCase (e.g., "Zayin", "Aleph")
 * @returns Rank icon path
 */
export function getEGORankIconPath(rank: string): string {
  return `/images/UI/EGO/${rank}.webp`
}

/**
 * Gets small EGO rank icon path for info panel
 * @param rank - EGO rank in PascalCase (e.g., "Zayin", "Aleph")
 * @returns Small rank icon path
 */
export function getEGOSmallRankIconPath(rank: string): string {
  return `/images/icon/EGO/${rank}.webp`
}

/**
 * Gets tier icon path for threadspin tier display
 * @param tier - Tier number (1-5)
 * @returns Tier icon path
 */
export function getEGOTierIconPath(tier: number): string {
  return `/images/UI/common/tier${tier}.webp`
}

/**
 * Gets EGO info panel background path (sin-colored)
 * @param sin - Sin type in PascalCase (e.g., "Wrath")
 * @returns EGO info panel path
 */
export function getEGOInfoPanelPath(sin: string): string {
  return `/images/UI/formation/egoInfoPanel${sin}.webp`
}

/**
 * Gets EGO skill image path for awakening or corrosion
 * @param egoId - EGO identifier
 * @param skillType - 'awakening' or 'corrosion'
 * @returns EGO skill image path
 */
export function getEGOSkillImagePath(egoId: string, skillType: 'awakening' | 'corrosion'): string {
  const imageFileName = skillType === 'awakening' ? 'awaken_profile.webp' : 'erosion_profile.webp'
  return `/images/EGO/${egoId}/${imageFileName}`
}

/**
 * Gets EGO detail page character image path
 * @param egoId - EGO identifier
 * @returns EGO character image path
 */
export function getEGODetailImagePath(egoId: string): string {
  return `/images/EGO/${egoId}/cg.webp`
}

/**
 * EGO Gift-specific utility functions
 */

/**
 * Gets EGO Gift icon path (128x128px gift image)
 * @param giftId - Gift ID
 * @returns Gift icon path
 */
export function getEGOGiftIconPath(giftId: string): string {
  return `/images/egoGift/${giftId}.webp`
}

/**
 * Gets EGO Gift grade icon path
 * @param tier - Gift tier (e.g., "1", "2", "3", "EX")
 * @returns Grade icon path
 */
export function getEGOGiftGradeIconPath(tier: string): string {
  return `/images/icon/egoGift/grade${tier}.webp`
}

/**
 * Gets EGO Gift enhancement icon path
 * @param level - Enhancement level (0, 1, 2, etc.)
 * @returns Enhancement icon path
 */
export function getEGOGiftEnhancementIconPath(level: number): string {
  return `/images/icon/egoGift/enhancement${level}.webp`
}

/**
 * Gets EGO Gift coin icon path
 * @returns Coin icon path
 */
export function getEGOGiftCoinIconPath(): string {
  return `/images/icon/egoGift/coin.webp`
}

/**
 * Planner-specific utility functions
 */

/**
 * Gets icon path for planner keywords (handles both status effects and sins)
 * @param keyword - Keyword in PascalCase (e.g., "Combustion", "Wrath")
 * @returns Icon path
 */
export function getPlannerKeywordIconPath(keyword: string): string {
  // Check if keyword is a sin type
  if ((SINS as readonly string[]).includes(keyword)) {
    return getSinIconPath(keyword)
  }
  // Otherwise treat as status effect
  return getStatusEffectIconPath(keyword)
}

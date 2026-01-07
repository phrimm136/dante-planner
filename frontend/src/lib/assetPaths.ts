import { AFFINITIES, type SkillAttributeType } from './constants'

/**
 * Removes bracket notation from strings used in game data
 * Example: "[yiSang]" -> "Yisang", "[rupture]" -> "rupture"
 */
export function parseBracketNotation(valueWithBrackets: string): string {
  return valueWithBrackets.replace(/[[\]]/g, '')
}

/**
 * Gets the selected indicator overlay path (checkmark/highlight for selected items)
 * Used across identity cards, EGO cards, and other selectable items in formation
 * @returns Selected indicator path
 */
export function getSelectedIndicatorPath(): string {
  return `/images/UI/formation/selected.webp`
}

/**
 * Gets the image path for an identity card (gacksung|normal_info)
 */
export function getIdentityInfoImagePath(identityId: string, identityUptie = 4): string {
  if (identityUptie < 3 || identityId.endsWith('01')) {
    return `/images/identity/${identityId}/normal_info.webp`
  }
  return `/images/identity/${identityId}/gacksung_info.webp`
}

/**
 * Gets the image path for an identity card (gacksung|normal_profile)
 */
export function getIdentityProfileImagePath(identityId: string, identityUptie = 4): string {
  if (identityUptie < 3) {
    return `/images/identity/${identityId}/normal_profile.webp`
  }
  return `/images/identity/${identityId}/gacksung_profile.webp`
}

/**
 * Gets the fallback image path for an identity card (normal_info)
 */
export function getIdentityImageFallbackPath(identityId: string): string {
  return `/images/identity/${identityId}/normal_info.webp`
}

/**
 * Gets the image path for an uptie frame based on rank and uptie level
 * @param rank - Rank (1-3)
 * @param uptie - Uptie level (1-4), defaults to 4
 */
export function getUptieFramePath(rank: number, uptie = 4): string {
  return `/images/UI/formation/${String(rank)}Rank${String(uptie)}UptieFrame.webp`
}

/**
 * Gets the image path for sinner background based on rank
 */
export function getSinnerBGPath(rank: number): string {
  return `/images/UI/formation/${String(rank)}RankSinnerBG.webp`
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
 * Gets rarity icon path based on grade
 */
export function getRarityIconPath(grade: number): string {
  return `/images/UI/identity/rarity${String(grade)}.webp`
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
  variantIndex = 0,
  isUptie4 = false
): string {
  const slotNum = String(skillSlot).padStart(2, '0')
  const variantSuffix = variantIndex > 0 ? `-${String(variantIndex + 1)}` : ''
  const uptieSuffix = isUptie4 ? '_4' : ''

  return `/images/identity/${identityId}/skill${slotNum}${variantSuffix}${uptieSuffix}.webp`
}

/**
 * Gets skill frame image path based on attribute type and skill slot
 * @param attributeType - Skill attribute type (e.g., "CRIMSON", "NEUTRAL")
 * @param skillSlot - Skill slot (1-3 for offensive, 4 for defense)
 * @returns Frame image path
 */
export function getSkillFramePath(attributeType: SkillAttributeType | undefined, skillSlot: number): string {
  // Defense skills or undefined attribute use NEUTRAL
  const attr = attributeType ?? 'NEUTRAL'
  const frameLevel = skillSlot <= 3 ? skillSlot : 1
  return `/images/UI/skillFrame/${attr}${String(frameLevel)}.webp`
}

/**
 * Gets skill frame background image path
 * @param attributeType - Skill attribute type (e.g., "CRIMSON", "NEUTRAL")
 * @param skillSlot - Skill slot (1-3 for offensive, 4 for defense)
 * @returns Frame background image path
 */
export function getSkillFrameBGPath(attributeType: SkillAttributeType | undefined, skillSlot: number): string {
  const attr = attributeType ?? 'NEUTRAL'
  const frameLevel = skillSlot <= 3 ? skillSlot : 1
  return `/images/UI/skillFrame/${attr}${String(frameLevel)}BG.webp`
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
 * @param attributeType - Skill attribute type (e.g., "CRIMSON", "NEUTRAL")
 * @returns Attack type frame path
 */
export function getAttackTypeFramePath(attributeType: SkillAttributeType): string {
  return `/images/UI/skillFrame/attackType${attributeType}.webp`
}

/**
 * Gets attack type frame background path
 * @param attributeType - Skill attribute type (e.g., "CRIMSON", "NEUTRAL")
 * @returns Attack type frame background path
 */
export function getAttackTypeFrameBGPath(attributeType: SkillAttributeType): string {
  return `/images/UI/skillFrame/attackTypeBG${attributeType}.webp`
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
  return `/images/UI/common/coin${String(coinIndex + 1)}.webp`
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
  return `/images/ego/${egoId}/cg.webp`
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
  return `/images/UI/ego/${rank}.webp`
}

/**
 * Gets small EGO rank icon path for info panel
 * @param rank - EGO rank in PascalCase (e.g., "Zayin", "Aleph")
 * @returns Small rank icon path
 */
export function getEGOSmallRankIconPath(rank: string): string {
  return `/images/icon/ego/${rank}.webp`
}

/**
 * Gets tier icon path for threadspin tier display
 * @param tier - Tier number (1-5)
 * @returns Tier icon path
 */
export function getEGOTierIconPath(tier: number): string {
  return `/images/UI/common/tier${String(tier)}.webp`
}

/**
 * Gets EGO info panel background path (attribute-colored)
 * @param attribute - Attribute type (e.g., "CRIMSON", "AZURE")
 * @returns EGO info panel path
 */
export function getEGOInfoPanelPath(attribute: string): string {
  return `/images/UI/formation/egoInfoPanel${attribute}.webp`
}

/**
 * Gets EGO skill image path for awaken or erosion
 * @param egoId - EGO identifier
 * @param skillType - 'awaken' or 'erosion'
 * @returns EGO skill image path
 */
export function getEGOSkillImagePath(egoId: string, skillType: 'awaken' | 'erosion'): string {
  const imageFileName = skillType === 'awaken' ? 'awaken_profile.webp' : 'erosion_profile.webp'
  return `/images/ego/${egoId}/${imageFileName}`
}

/**
 * Gets EGO detail page character image path
 * @param egoId - EGO identifier
 * @returns EGO character image path
 */
export function getEGODetailImagePath(egoId: string): string {
  return `/images/ego/${egoId}/cg.webp`
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
  return `/images/icon/egoGift/${giftId}.webp`
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
  return `/images/UI/egoGift/enhancement${String(level)}.webp`
}

/**
 * Gets EGO Gift coin icon path
 * @returns Coin icon path
 */
export function getEGOGiftCoinIconPath(): string {
  return `/images/icon/egoGift/coin.webp`
}

/**
 * Gets EGO Gift background image path
 * @returns Background image path
 */
export function getEGOGiftBackgroundPath(): string {
  return `/images/UI/egoGift/bg.webp`
}

/**
 * Gets EGO Gift enhanced background image path (level 1 overlay)
 * @returns Enhanced background path
 */
export function getEGOGiftEnhancedBackgroundPath(): string {
  return `/images/UI/egoGift/bgEnhanced.webp`
}

/**
 * Gets EGO Gift enhanced background image path (level 2 base)
 * @returns Enhanced background level 2 path
 */
export function getEGOGiftEnhanced2BackgroundPath(): string {
  return `/images/UI/egoGift/bgEnhanced2.webp`
}

/**
 * Gets EGO Gift tier EX icon path
 * @returns Tier EX icon path
 */
export function getEGOGiftTierEXPath(): string {
  return `/images/UI/egoGift/tierEX.webp`
}

/**
 * Planner-specific utility functions
 */

/**
 * Mapping from affinity names to sin names for asset paths
 * Used internally to convert data format to file naming convention
 */
const AFFINITY_TO_SIN_NAME: Record<string, string> = {
  CRIMSON: 'Wrath',
  SCARLET: 'Lust',
  AMBER: 'Sloth',
  SHAMROCK: 'Gluttony',
  AZURE: 'Gloom',
  INDIGO: 'Pride',
  VIOLET: 'Envy',
}

/**
 * Gets icon path for planner keywords (handles both status effects and affinities)
 * @param keyword - Keyword (e.g., "Combustion", "CRIMSON")
 * @returns Icon path
 */
export function getPlannerKeywordIconPath(keyword: string): string {
  // Check if keyword is an affinity type
  if ((AFFINITIES as readonly string[]).includes(keyword)) {
    return getAffinityIconPath(keyword)
  }
  // Otherwise treat as status effect
  return getStatusEffectIconPath(keyword)
}

/**
 * Gets the image path for an affinity icon
 * Converts affinity name to sin name for file path
 * @param affinity - Affinity name (e.g., "CRIMSON", "AZURE")
 * @returns Affinity icon path using sin name
 */
export function getAffinityIconPath(affinity: string): string {
  const sinName = AFFINITY_TO_SIN_NAME[affinity] || affinity
  return `/images/icon/sin/${sinName}.webp`
}

/**
 * Start Buff-specific utility functions
 */

/**
 * Gets Start Buff icon path
 * @param baseId - Base buff ID (100-109)
 * @returns Buff icon path
 */
export function getStartBuffIconPath(baseId: number): string {
  return `/images/UI/MD6/StartBuffIcon_${String(baseId)}.webp`
}

/**
 * Gets Start Buff pane background path
 * @returns Pane background path
 */
export function getStartBuffPanePath(): string {
  return `/images/UI/MD6/startBuffPane.webp`
}

/**
 * Gets Start Buff highlight overlay path
 * @returns Highlight overlay path
 */
export function getStartBuffHighlightPath(): string {
  return `/images/UI/MD6/startBuffHighlight.webp`
}

/**
 * Gets Start Buff star light decoration path
 * @returns Star light path
 */
export function getStartBuffStarLightPath(): string {
  return `/images/UI/MD6/starLight.webp`
}

/**
 * Gets Start Buff enhancement button background path
 * @param level - Enhancement level (0 for unselected, 1 for +, 2 for ++)
 * @returns Enhancement button background path
 */
export function getStartBuffEnhancementBgPath(level: 0 | 1 | 2): string {
  if (level === 0) {
    return `/images/UI/MD6/startBuffEnhancementUnselected.webp`
  }
  return `/images/UI/MD6/startBuffEnhancement${String(level)}Selected.webp`
}

/**
 * Gets Start Buff enhancement icon path
 * @param level - Enhancement level (0 for default, 1 for +, 2 for ++)
 * @returns Enhancement icon path
 */
export function getStartBuffEnhancementIconPath(level: 0 | 1 | 2): string {
  if (level === 0) {
    return `/images/UI/MD6/startBuffEnhancementIcon.webp`
  }
  return `/images/UI/egoGift/enhancement${String(level)}.webp`
}

/**
 * Gets Start Buff mini card background path
 * @param version - Mirror Dungeon version (from backend config)
 * @returns Mini card background path
 */
export function getStartBuffMiniPath(version: number): string {
  return `/images/UI/MD${String(version)}/startBuffMini.webp`
}

/**
 * Gets Start Buff mini card highlight overlay path
 * @param version - Mirror Dungeon version (from backend config)
 * @returns Mini card highlight path
 */
export function getStartBuffMiniHighlightPath(version: number): string {
  return `/images/UI/MD${String(version)}/startBuffMiniHighlight.webp`
}

/**
 * Theme Pack-specific utility functions
 */

/**
 * Gets composed theme pack image path
 * These are pre-composed images (base + boss + frame + icons, no text)
 * @param packId - Theme pack ID (e.g., "1001", "1002")
 * @returns Composed theme pack image path
 */
export function getThemePackImagePath(packId: string): string {
  return `/images/themePack/${packId}.webp`
}

/**
 * Gets EGO type icon path
 * @Param egoType - EGO Type (ZAYIN, TETH, HE, WAW, ALEPH)
 * @Returns Icon path
 */
export function getEGOTypeIconPath(egoType: string): string {
  return `/images/icon/ego/${egoType}.webp`
}

/**
 * Battle Keyword-specific utility functions
 */

/**
 * Gets battle keyword icon path
 * Used in skill/passive descriptions for keywords like Sinking, Rupture, Burn
 * @param iconIdOrKey - Icon ID from battleKeywords data, or keyword key as fallback
 * @returns Battle keyword icon path
 */
export function getBattleKeywordIconPath(iconIdOrKey: string): string {
  return `/images/icon/battleKeywords/${iconIdOrKey}.webp`
}

/**
 * Panic-specific utility functions
 */

/**
 * Gets panic type icon path
 * @param panicType - Panic type ID (e.g., 1001, 9999)
 * @returns Panic icon path
 */
export function getPanicIconPath(panicType: number): string {
  return `/images/icon/panic/${String(panicType)}.webp`
}

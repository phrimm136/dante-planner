/**
 * egoGiftFilter.ts
 *
 * Utility functions for EGO Gift filtering logic.
 * Extracted from EGOGiftList for testability.
 */

import type { EGOGiftDifficulty, EGOGiftTier } from '@/lib/constants'
import { EGO_GIFT_TIERS, EGO_GIFT_TIER_TAGS } from '@/lib/constants'

/** Map tier tag (TIER_1) to display tier (I) */
const TIER_TAG_TO_DISPLAY: Record<string, EGOGiftTier> = Object.fromEntries(
  EGO_GIFT_TIER_TAGS.map((tag, index) => [tag, EGO_GIFT_TIERS[index]])
)

/**
 * Extract display tier from gift tag array
 * Returns the first matching tier or undefined
 *
 * @example
 * extractTier(['TIER_3', 'GIFT']) // Returns 'III'
 * extractTier(['GIFT']) // Returns undefined
 */
export function extractTier(tag: readonly string[]): EGOGiftTier | undefined {
  for (const t of tag) {
    const tier = TIER_TAG_TO_DISPLAY[t]
    if (tier) return tier
  }
  return undefined
}

/**
 * Derive difficulty from hardOnly/extremeOnly flags
 * Priority: extremeOnly > hardOnly > normal
 *
 * @example
 * deriveDifficulty({ extremeOnly: true }) // Returns 'extreme'
 * deriveDifficulty({ hardOnly: true }) // Returns 'hard'
 * deriveDifficulty({}) // Returns 'normal'
 */
export function deriveDifficulty(gift: {
  hardOnly?: boolean
  extremeOnly?: boolean
}): EGOGiftDifficulty {
  if (gift.extremeOnly) return 'extreme'
  if (gift.hardOnly) return 'hard'
  return 'normal'
}

/**
 * Check if a gift matches the selected keywords filter
 * Empty selection = no filter (returns true)
 * OR logic: matches if gift keyword is in selected keywords
 */
export function matchesKeywordFilter(
  giftKeyword: string | null,
  selectedKeywords: Set<string>
): boolean {
  if (selectedKeywords.size === 0) return true
  if (giftKeyword === null) return selectedKeywords.has('None')
  return selectedKeywords.has(giftKeyword)
}

/**
 * Check if a gift matches the selected difficulties filter
 * Empty selection = no filter (returns true)
 * OR logic: matches if derived difficulty is in selected difficulties
 */
export function matchesDifficultyFilter(
  gift: { hardOnly?: boolean; extremeOnly?: boolean },
  selectedDifficulties: Set<EGOGiftDifficulty>
): boolean {
  if (selectedDifficulties.size === 0) return true
  const difficulty = deriveDifficulty(gift)
  return selectedDifficulties.has(difficulty)
}

/**
 * Check if a gift matches the selected tiers filter
 * Empty selection = no filter (returns true)
 * OR logic: matches if extracted tier is in selected tiers
 */
export function matchesTierFilter(
  tag: readonly string[],
  selectedTiers: Set<EGOGiftTier>
): boolean {
  if (selectedTiers.size === 0) return true
  const tier = extractTier(tag)
  return tier !== undefined && selectedTiers.has(tier)
}

/**
 * Check if a gift matches the selected theme packs filter
 * Empty selection = no filter (returns true)
 * OR logic: matches if any gift theme pack is in selected theme packs
 */
export function matchesThemePackFilter(
  themePacks: readonly (string | number)[] | undefined,
  selectedThemePacks: Set<string>
): boolean {
  if (selectedThemePacks.size === 0) return true
  const giftThemePacks = themePacks ?? []
  return giftThemePacks.some((tp) => selectedThemePacks.has(String(tp)))
}

/**
 * Check if a gift matches the selected attribute types filter
 * Empty selection = no filter (returns true)
 * OR logic: matches if gift attribute type is in selected types
 */
export function matchesAttributeTypeFilter(
  attributeType: string | undefined,
  selectedAttributeTypes: Set<string>
): boolean {
  if (selectedAttributeTypes.size === 0) return true
  return attributeType !== undefined && selectedAttributeTypes.has(attributeType)
}

/**
 * Check if a gift matches the fusioned filter
 * Empty selection = no filter (returns true)
 * OR logic: N = not fusioned, Y = fusioned
 */
export function matchesFusionedFilter(
  fusioned: boolean | undefined,
  selectedFusioned: Set<string>
): boolean {
  if (selectedFusioned.size === 0) return true
  const isFusioned = fusioned === true
  if (selectedFusioned.has('Y') && isFusioned) return true
  if (selectedFusioned.has('N') && !isFusioned) return true
  return false
}

/**
 * Check if a gift matches the theme pack exclusive filter
 * Empty selection = no filter (returns true)
 * OR logic: N = not exclusive, Y = exclusive (has non-empty themePack)
 */
export function matchesExclusiveFilter(
  themePacks: readonly string[] | undefined,
  selectedExclusive: Set<string>
): boolean {
  if (selectedExclusive.size === 0) return true
  const isExclusive = (themePacks ?? []).length > 0
  if (selectedExclusive.has('Y') && isExclusive) return true
  if (selectedExclusive.has('N') && !isExclusive) return true
  return false
}

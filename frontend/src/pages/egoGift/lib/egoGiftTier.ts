/**
 * egoGiftTier.ts
 *
 * The one reading of a gift's TIER_ tags, plus the Roman-numeral view the tier
 * chips and the tier facet select on.
 */

import { EGO_GIFT_TIER_TAGS } from '@/shared/gameData'
import type { EGOGiftTier } from '@/shared/gameData'

/** Tier as the data spells it: a TIER_ tag with its prefix stripped. */
export type EGOGiftTierValue = '1' | '2' | '3' | '4' | '5' | 'EX'

const TIER_BY_TAG = new Map<string, EGOGiftTierValue>(
  EGO_GIFT_TIER_TAGS.map((tag) => [tag, tag.replace('TIER_', '') as EGOGiftTierValue] as const),
)

const ROMAN_BY_TIER: Record<EGOGiftTierValue, EGOGiftTier> = {
  '1': 'I',
  '2': 'II',
  '3': 'III',
  '4': 'IV',
  '5': 'V',
  EX: 'EX',
}

/**
 * Tier carried by a gift's tag array.
 * TIER_EX wins wherever it sits; otherwise the first recognised TIER_ tag does.
 */
export function parseTier(tags: readonly string[]): EGOGiftTierValue | null {
  if (tags.includes('TIER_EX')) return 'EX'

  for (const tag of tags) {
    const tier = TIER_BY_TAG.get(tag)
    if (tier) return tier
  }

  return null
}

/** Roman numeral for a tier; an absent tier passes straight through. */
export function toRomanTier(tier: EGOGiftTierValue | null): EGOGiftTier | undefined {
  return tier === null ? undefined : ROMAN_BY_TIER[tier]
}

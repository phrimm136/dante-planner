import type { EGOGiftListItem } from '../types/EGOGiftTypes'
import type { SortMode } from '@/shared/filter'
import { KEYWORD_ORDER } from '@/shared/gameData'

/** Unrecognized and absent keywords both sort where 'None' sits. */
const NONE_CATEGORY_INDEX = KEYWORD_ORDER.indexOf('None')

/**
 * Get the category index for sorting
 * Returns index in KEYWORD_ORDER, or None index if no match
 */
function getCategoryIndex(keyword: string | null): number {
  if (!keyword) return NONE_CATEGORY_INDEX
  const index = KEYWORD_ORDER.indexOf(keyword as (typeof KEYWORD_ORDER)[number])
  return index !== -1 ? index : NONE_CATEGORY_INDEX
}

/**
 * Extract tier from tag array (e.g., "TIER_2" -> "2", "TIER_EX" -> "EX")
 * If TIER_EX exists, use it; otherwise use any TIER_ tag
 */
export function extractTier(tag: string[]): string | null {
  const exTier = tag.find((t) => t === 'TIER_EX')
  if (exTier) return 'EX'
  return tag.find((t) => t.startsWith('TIER_'))?.replace('TIER_', '') || null
}

/**
 * Get tier sort value (EX = highest, then 5, 4, 3, 2, 1)
 */
function getTierValue(tag: string[]): number {
  const tier = extractTier(tag)
  if (!tier) return 999
  if (tier === 'EX') return 0
  const tierNum = parseInt(tier, 10)
  return isNaN(tierNum) ? 999 : 6 - tierNum // 5->1, 4->2, 3->3, 2->4, 1->5
}

type SortKey = (gift: EGOGiftListItem) => number

const categoryKey: SortKey = (gift) => getCategoryIndex(gift.keyword)
const tierKey: SortKey = (gift) => getTierValue(gift.tag)
const idKey: SortKey = (gift) => parseInt(gift.id, 10)

/** Ascending key ladder per sort mode; the first key that differs decides. */
const SORT_KEYS: Record<SortMode, readonly SortKey[]> = {
  'tier-first': [tierKey, categoryKey, idKey],
  'keyword-first': [categoryKey, tierKey, idKey],
}

/**
 * Sort EGO Gifts based on sort mode
 */
export function sortEGOGifts(gifts: EGOGiftListItem[], sortMode: SortMode): EGOGiftListItem[] {
  const keys = SORT_KEYS[sortMode]

  return [...gifts].sort((a, b) => {
    for (const key of keys) {
      const difference = key(a) - key(b)
      if (difference !== 0) return difference
    }
    return 0
  })
}

import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { SortMode } from '@/components/common/Sorter'
import { KEYWORD_ORDER } from './constants'

/**
 * Get the category index for sorting
 * Returns index in KEYWORD_ORDER, or None index if no match
 */
function getCategoryIndex(keyword: string | null): number {
  if (!keyword) return KEYWORD_ORDER.indexOf('None')
  const index = KEYWORD_ORDER.indexOf(keyword as (typeof KEYWORD_ORDER)[number])
  // Return index if found, otherwise treat as None (last in order)
  return index !== -1 ? index : KEYWORD_ORDER.indexOf('None')
}

/**
 * Extract tier from tag array (e.g., "TIER_2" -> "2", "TIER_EX" -> "EX")
 * If TIER_EX exists, use it; otherwise use any TIER_ tag
 */
export function extractTier(tag: string[]): string | null {
  const exTier = tag.find(t => t === 'TIER_EX')
  if (exTier) return 'EX'
  return tag.find(t => t.startsWith('TIER_'))?.replace('TIER_', '') || null
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

/**
 * Sort EGO Gifts based on sort mode
 */
export function sortEGOGifts(gifts: EGOGiftListItem[], sortMode: SortMode): EGOGiftListItem[] {
  return [...gifts].sort((a, b) => {
    const aCategoryIndex = getCategoryIndex(a.keyword)
    const bCategoryIndex = getCategoryIndex(b.keyword)
    const aTierValue = getTierValue(a.tag)
    const bTierValue = getTierValue(b.tag)
    const aId = parseInt(a.id, 10)
    const bId = parseInt(b.id, 10)

    if (sortMode === 'tier-first') {
      // tier -> category -> id
      if (aTierValue !== bTierValue) return aTierValue - bTierValue
      if (aCategoryIndex !== bCategoryIndex) return aCategoryIndex - bCategoryIndex
      return aId - bId
    } else {
      // category -> tier -> id
      if (aCategoryIndex !== bCategoryIndex) return aCategoryIndex - bCategoryIndex
      if (aTierValue !== bTierValue) return aTierValue - bTierValue
      return aId - bId
    }
  })
}

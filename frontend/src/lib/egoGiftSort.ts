import type { EGOGift } from '@/types/EGOGiftTypes'
import type { SortMode } from '@/components/common/Sorter'
import { KEYWORD_ORDER } from './constants'

/**
 * Get the category index for sorting
 * Returns index in KEYWORD_ORDER, or Common index if no match
 */
function getCategoryIndex(category: string): number {
  const index = KEYWORD_ORDER.indexOf(category as typeof KEYWORD_ORDER[number])
  // Return index if found, otherwise treat as Common (last in order)
  return index !== -1 ? index : KEYWORD_ORDER.indexOf('Common')
}

/**
 * Get tier sort value (EX = highest, then 5, 4, 3, 2, 1)
 */
function getTierValue(tier: string): number {
  if (tier === 'EX') return 0
  const tierNum = parseInt(tier, 10)
  return isNaN(tierNum) ? 999 : 6 - tierNum // 5->1, 4->2, 3->3, 2->4, 1->5
}

/**
 * Sort EGO Gifts based on sort mode
 */
export function sortEGOGifts(gifts: EGOGift[], sortMode: SortMode): EGOGift[] {
  return [...gifts].sort((a, b) => {
    const aCategoryIndex = getCategoryIndex(a.category)
    const bCategoryIndex = getCategoryIndex(b.category)
    const aTierValue = getTierValue(a.tier)
    const bTierValue = getTierValue(b.tier)
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

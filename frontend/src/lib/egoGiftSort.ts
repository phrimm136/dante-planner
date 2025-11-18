import type { EGOGift } from '@/types/EGOGiftTypes'
import type { SortMode } from '@/components/common/Sorter'
import { KEYWORD_ORDER } from './constants'

/**
 * Get the keyword category index for sorting
 * Returns index in KEYWORD_ORDER, or Common index if no match
 */
function getKeywordIndex(keywords: string[]): number {
  // Find first keyword that matches KEYWORD_ORDER
  for (const keyword of keywords) {
    const index = KEYWORD_ORDER.indexOf(keyword as typeof KEYWORD_ORDER[number])
    if (index !== -1) {
      return index
    }
  }
  // No match found - treat as Common (last in order)
  return KEYWORD_ORDER.indexOf('Common')
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
    const aKeywordIndex = getKeywordIndex(a.keywords)
    const bKeywordIndex = getKeywordIndex(b.keywords)
    const aTierValue = getTierValue(a.tier)
    const bTierValue = getTierValue(b.tier)
    const aId = parseInt(a.id, 10)
    const bId = parseInt(b.id, 10)

    if (sortMode === 'tier-first') {
      // tier -> keyword -> id
      if (aTierValue !== bTierValue) return aTierValue - bTierValue
      if (aKeywordIndex !== bKeywordIndex) return aKeywordIndex - bKeywordIndex
      return aId - bId
    } else {
      // keyword -> tier -> id
      if (aKeywordIndex !== bKeywordIndex) return aKeywordIndex - bKeywordIndex
      if (aTierValue !== bTierValue) return aTierValue - bTierValue
      return aId - bId
    }
  })
}

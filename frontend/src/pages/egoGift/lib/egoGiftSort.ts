import type { EGOGiftEntity } from '../types/EGOGiftTypes'
import type { SortMode } from '@/shared/filter'
import { KEYWORD_ORDER } from '@/shared/gameData'
import { parseTier } from './egoGiftTier'

const NONE_CATEGORY_INDEX = KEYWORD_ORDER.indexOf('None')

function getCategoryIndex(keyword: string | null): number {
  if (!keyword) return NONE_CATEGORY_INDEX
  const index = KEYWORD_ORDER.indexOf(keyword as (typeof KEYWORD_ORDER)[number])
  return index !== -1 ? index : NONE_CATEGORY_INDEX
}

function getTierValue(tag: string[]): number {
  const tier = parseTier(tag)
  if (!tier) return 999
  if (tier === 'EX') return 0
  const tierNum = parseInt(tier, 10)
  return isNaN(tierNum) ? 999 : 6 - tierNum
}

type SortKey = (gift: EGOGiftEntity) => number

const categoryKey: SortKey = (gift) => getCategoryIndex(gift.keyword)
const tierKey: SortKey = (gift) => getTierValue(gift.tag)
const idKey: SortKey = (gift) => parseInt(gift.id, 10)

const SORT_KEYS: Record<SortMode, readonly SortKey[]> = {
  'tier-first': [tierKey, categoryKey, idKey],
  'keyword-first': [categoryKey, tierKey, idKey],
}

export function sortEGOGifts(gifts: EGOGiftEntity[], sortMode: SortMode): EGOGiftEntity[] {
  const keys = SORT_KEYS[sortMode]

  return [...gifts].sort((a, b) => {
    for (const key of keys) {
      const difference = key(a) - key(b)
      if (difference !== 0) return difference
    }
    return 0
  })
}

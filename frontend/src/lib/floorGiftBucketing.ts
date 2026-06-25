import type { EGOGiftListItem } from '@/pages/egoGift'
import type { SortMode } from '@/components/common/Sorter'
import type { DungeonIdx } from './constants'
import { DUNGEON_IDX } from './constants'
import { sortEGOGifts } from '@/pages/egoGift'

/**
 * Bucket and sort gifts for a floor's selector dialog.
 *
 * Bucketing under themed-reachability semantics of `gift.themePack`:
 *   1. themed to this pack (pack-exclusive + recipe-derived themed fusions)
 *   2. general (empty themePack — acquirable in any pack via random fusion)
 *
 * Hidden: gifts whose themePack is non-empty but does not include this pack —
 * those are themed-restricted to other packs and genuinely unobtainable here.
 *
 * Difficulty filter precedes bucketing: gifts marked extremeOnly/hardOnly are
 * dropped when the floor's dungeon index is below their requirement.
 */
export function bucketAndSortFloorGifts(
  gifts: EGOGiftListItem[],
  themePackId: string,
  difficulty: DungeonIdx,
  sortMode: SortMode
): EGOGiftListItem[] {
  const difficultyFiltered = gifts.filter((gift) => {
    if (gift.extremeOnly && difficulty < DUNGEON_IDX.EXTREME) return false
    if (gift.hardOnly && difficulty < DUNGEON_IDX.HARD) return false
    return true
  })

  const themedToThis: EGOGiftListItem[] = []
  const general: EGOGiftListItem[] = []

  for (const g of difficultyFiltered) {
    const themed = g.themePack ?? []
    if (themed.length === 0) {
      general.push(g)
    } else if (themed.includes(themePackId)) {
      themedToThis.push(g)
    }
    // else: themed to other packs only — hidden
  }

  return [
    ...sortEGOGifts(themedToThis, sortMode),
    ...sortEGOGifts(general, sortMode),
  ]
}

import type { EGOGiftEntity } from '@/pages/egoGift'
import type { SortMode } from '@/shared/filter'
import type { DungeonIdx } from '@/shared/gameData'
import { DUNGEON_IDX } from '@/shared/gameData'
import { sortEGOGifts } from '@/pages/egoGift'

export function bucketAndSortFloorGifts(
  gifts: EGOGiftEntity[],
  themePackId: string,
  difficulty: DungeonIdx,
  sortMode: SortMode,
): EGOGiftEntity[] {
  const difficultyFiltered = gifts.filter((gift) => {
    if (gift.extremeOnly && difficulty < DUNGEON_IDX.EXTREME) return false
    if (gift.hardOnly && difficulty < DUNGEON_IDX.HARD) return false
    return true
  })

  const themedToThis: EGOGiftEntity[] = []
  const general: EGOGiftEntity[] = []

  for (const g of difficultyFiltered) {
    const themed = g.themePack ?? []
    if (themed.length === 0) {
      general.push(g)
    } else if (themed.includes(themePackId)) {
      themedToThis.push(g)
    }
    // else: themed to other packs only — hidden
  }

  return [...sortEGOGifts(themedToThis, sortMode), ...sortEGOGifts(general, sortMode)]
}

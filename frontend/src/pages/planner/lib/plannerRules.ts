import { lookupByGiftId, giftDisplayName } from '@/pages/egoGift'
import type { FloorThemeSelection } from '@/pages/themePack'
import type { EGOGiftSpec } from '@/pages/egoGift'
import { DUNGEON_IDX, allowedDifficulties } from '@/shared/gameData'
import type { DungeonIdx, EncodedGiftId, MDCategory } from '@/shared/gameData'

export function isGiftAffordableForThemePack(gift: EGOGiftSpec, themePackId: string): boolean {
  return gift.themePack.length === 0 || gift.themePack.includes(themePackId)
}

export function getUnaffordableGiftIds(
  giftIds: ReadonlySet<EncodedGiftId>,
  themePackId: string,
  egoGiftSpec: Record<string, EGOGiftSpec>,
): EncodedGiftId[] {
  return Array.from(giftIds).filter((giftId) => {
    const gift = lookupByGiftId(giftId, egoGiftSpec)
    if (!gift) return false
    return !isGiftAffordableForThemePack(gift, themePackId)
  })
}

export function getUnaffordableGiftNames(
  giftIds: ReadonlySet<EncodedGiftId>,
  themePackId: string,
  egoGiftSpec: Record<string, EGOGiftSpec>,
  egoGiftI18n: Record<string, string>,
): { ids: EncodedGiftId[]; names: string[] } {
  const ids = getUnaffordableGiftIds(giftIds, themePackId, egoGiftSpec)
  const names = ids.map((id) => giftDisplayName(id, egoGiftI18n))
  return { ids, names }
}

export function canSelectFloorThemePack(
  floorIndex: number,
  floorSelections: FloorThemeSelection[],
): boolean {
  if (floorIndex === 0) return true

  const previousFloor = floorSelections[floorIndex - 1]
  return (
    previousFloor !== undefined &&
    previousFloor.themePackId !== null &&
    previousFloor.themePackId !== ''
  )
}

export function offeredFloorDifficulties(
  category: MDCategory,
  floorIndex: number,
  earlierFloorsAllNormal: boolean,
): DungeonIdx[] {
  return (allowedDifficulties(category, floorIndex) ?? []).filter(
    (difficulty) => difficulty !== DUNGEON_IDX.NORMAL || earlierFloorsAllNormal,
  )
}

export function usedFloorThemePackIds(
  floorSelections: readonly FloorThemeSelection[],
  floorIndex: number,
  floorCount: number,
): string[] {
  return floorSelections
    .slice(0, floorCount)
    .flatMap((floor, i) => (i !== floorIndex && floor.themePackId ? [floor.themePackId] : []))
}

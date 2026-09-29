import type { FloorSelectionValue } from '../floorRules'

export type OutcomeFloor = {
  themePackId: string | null
  difficulty: number | null
  giftIds: string[]
}

export const outcomeFloor = (floor: FloorSelectionValue): OutcomeFloor => ({
  themePackId: floor.themePackId ?? null,
  difficulty: floor.difficulty ?? null,
  giftIds: [...floor.giftIds],
})

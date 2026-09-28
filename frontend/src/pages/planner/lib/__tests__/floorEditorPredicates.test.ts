import { describe, it, expect } from 'vitest'

import { DUNGEON_IDX } from '@/shared/gameData'
import {
  normalAllowedAt,
  packIdsUsedElsewhere,
  packSelectableAt,
  violationsForCategory,
} from '../floorRules'

const floor = (themePackId: string | null, difficulty: number = DUNGEON_IDX.HARD) => ({
  themePackId,
  difficulty,
})

describe('normalAllowedAt', () => {
  it('allows NORMAL on 5F floor 1 and after NORMAL floors', () => {
    expect(normalAllowedAt([], '5F', 0)).toBe(true)
    expect(normalAllowedAt([floor('1001', DUNGEON_IDX.NORMAL), floor('1002', 0)], '5F', 2)).toBe(
      true,
    )
  })

  // Behavior Inventory 7: NORMAL never follows HARD, adjacent or not
  it('refuses NORMAL after any earlier HARD floor', () => {
    expect(normalAllowedAt([floor('1001'), floor('1002', 0)], '5F', 2)).toBe(false)
  })

  it('refuses NORMAL where the table does not allow it', () => {
    expect(normalAllowedAt([], '10F', 0)).toBe(false)
    expect(normalAllowedAt([], '5F', 5)).toBe(false)
  })
})

describe('packSelectableAt', () => {
  it('allows floor 1 and a floor after a chosen pack', () => {
    expect(packSelectableAt([], 0)).toBe(true)
    expect(packSelectableAt([floor('1001')], 1)).toBe(true)
  })

  it('refuses a floor after an empty, null or missing pack', () => {
    expect(packSelectableAt([floor('')], 1)).toBe(false)
    expect(packSelectableAt([floor(null)], 1)).toBe(false)
    expect(packSelectableAt([floor('1001')], 2)).toBe(false)
  })
})

describe('violationsForCategory', () => {
  it('reports a range violation for a pack-held NORMAL floor under 10F', () => {
    const floors = Array.from({ length: 10 }, (_, i) => floor(`${1001 + i}`, i === 0 ? 0 : 1))
    expect(violationsForCategory(floors, '10F')).toEqual([
      { code: 'VALUE_OUT_OF_RANGE', path: 'floorSelections[0].difficulty' },
    ])
  })

  it('returns no violations for publishable floors', () => {
    const floors = Array.from({ length: 5 }, (_, i) => floor(`${1001 + i}`))
    expect(violationsForCategory(floors, '5F')).toEqual([])
  })
})

describe('packIdsUsedElsewhere', () => {
  // Behavior Inventory 1: a pack on a hidden floor is offerable again
  it('ignores packs past the category floor count and the floor being edited', () => {
    const floors = [
      floor('1001'),
      floor('1002'),
      floor(null),
      floor(null),
      floor(null),
      floor('1006'),
    ]
    expect(packIdsUsedElsewhere(floors, '5F', 1)).toEqual(['1001'])
  })
})

import { describe, it, expect } from 'vitest'
import { ThemePackDetailSchema, ThemePackListSchema } from '../ThemePackSchemas'

describe('ThemePackDetailSchema', () => {
  it('accepts valid theme pack detail', () => {
    const result = ThemePackDetailSchema.safeParse({
      exceptionConditions: [
        { dungeonIdx: 0, selectableFloors: [0, 1] },
        { dungeonIdx: 1, selectableFloors: [0] },
      ],
      nodeOption: {
        bossPool: [2060222],
        battlePool: [2060201],
        abBattlePool: [2060216],
        hardBattlePool: [2060219],
        hardAbBattlePool: [],
        eventPool: [901001, 901002],
      },
      egoGiftPool: [9001, 9002],
      specificEgoGiftPool: [9403, 9404],
      themePackConfig: { textColor: 'af241c' },
    })
    expect(result.success).toBe(true)
  })

  it('accepts infinity pack with undefined selectableFloors', () => {
    const result = ThemePackDetailSchema.safeParse({
      exceptionConditions: [
        { dungeonIdx: 2 },
      ],
      nodeOption: {
        bossPool: [],
        battlePool: [],
        abBattlePool: [],
        hardBattlePool: [],
        hardAbBattlePool: [],
        eventPool: [],
      },
      egoGiftPool: [],
      specificEgoGiftPool: [],
      themePackConfig: { textColor: '5ce6ff' },
    })
    expect(result.success).toBe(true)
  })

  it('does not include difficulty in parsed output', () => {
    const result = ThemePackDetailSchema.safeParse({
      exceptionConditions: [],
      nodeOption: {
        bossPool: [], battlePool: [], abBattlePool: [],
        hardBattlePool: [], hardAbBattlePool: [], eventPool: [],
      },
      egoGiftPool: [],
      specificEgoGiftPool: [],
      themePackConfig: { textColor: 'af241c' },
    })
    expect(result.success).toBe(true)
    if (result.success) {
      expect('difficulty' in result.data).toBe(false)
    }
  })

  it('accepts nodeOption with specialEventPool', () => {
    const result = ThemePackDetailSchema.safeParse({
      exceptionConditions: [{ dungeonIdx: 0, selectableFloors: [0] }],
      nodeOption: {
        bossPool: [], battlePool: [], abBattlePool: [],
        hardBattlePool: [], hardAbBattlePool: [], eventPool: [901001],
        specialEventPool: [971001, 971002],
      },
      egoGiftPool: [],
      specificEgoGiftPool: [],
      themePackConfig: { textColor: 'af241c' },
    })
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.nodeOption.specialEventPool).toEqual([971001, 971002])
    }
  })

  it('rejects invalid dungeonIdx', () => {
    const result = ThemePackDetailSchema.safeParse({
      exceptionConditions: [{ dungeonIdx: 5 }],
      nodeOption: {
        bossPool: [], battlePool: [], abBattlePool: [],
        hardBattlePool: [], hardAbBattlePool: [], eventPool: [],
      },
      egoGiftPool: [],
      specificEgoGiftPool: [],
      themePackConfig: { textColor: 'af241c' },
    })
    expect(result.success).toBe(false)
  })
})

describe('ThemePackListSchema', () => {
  it('accepts valid list entry', () => {
    const result = ThemePackListSchema.safeParse({
      '1001': {
        exceptionConditions: [{ dungeonIdx: 0, selectableFloors: [0] }],
        specificEgoGiftPool: [],
        themePackConfig: { textColor: 'af241c' },
      },
    })
    expect(result.success).toBe(true)
  })
})

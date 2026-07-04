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
      featuredBosses: [{ unitId: 71001, portraitId: 91001 }],
    })
    expect(result.success).toBe(true)
  })

  it('accepts infinity pack with undefined selectableFloors', () => {
    const result = ThemePackDetailSchema.safeParse({
      exceptionConditions: [{ dungeonIdx: 2 }],
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
      featuredBosses: [],
    })
    expect(result.success).toBe(true)
  })

  it('does not include difficulty in parsed output', () => {
    const result = ThemePackDetailSchema.safeParse({
      exceptionConditions: [],
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
      themePackConfig: { textColor: 'af241c' },
      featuredBosses: [],
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
        bossPool: [],
        battlePool: [],
        abBattlePool: [],
        hardBattlePool: [],
        hardAbBattlePool: [],
        eventPool: [901001],
        specialEventPool: [971001, 971002],
      },
      egoGiftPool: [],
      specificEgoGiftPool: [],
      themePackConfig: { textColor: 'af241c' },
      featuredBosses: [],
    })
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.nodeOption.specialEventPool).toEqual([971001, 971002])
    }
  })

  it('accepts hidden theme fields', () => {
    const result = ThemePackDetailSchema.safeParse({
      exceptionConditions: [{ dungeonIdx: 1, selectableFloors: [2, 3, 4] }, { dungeonIdx: 2 }],
      nodeOption: {
        bossPool: [2070401],
        battlePool: [],
        abBattlePool: [],
        hardBattlePool: [],
        hardAbBattlePool: [],
        eventPool: [971055],
        specialEventPool: [971089],
      },
      egoGiftPool: [9003],
      specificEgoGiftPool: [],
      themePackConfig: { textColor: 'e5c6a0' },
      featuredBosses: [{ unitId: 70401, portraitId: 90401 }],
      hiddenThemeRate: 0.0002,
      fixedRewardEgoGifts: [9242, 9083, 9082, 9081],
    })
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.hiddenThemeRate).toBe(0.0002)
      expect(result.data.fixedRewardEgoGifts).toEqual([9242, 9083, 9082, 9081])
    }
  })

  it('accepts detail without hidden theme fields', () => {
    const result = ThemePackDetailSchema.safeParse({
      exceptionConditions: [{ dungeonIdx: 0, selectableFloors: [0] }],
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
      themePackConfig: { textColor: 'af241c' },
      featuredBosses: [],
    })
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.hiddenThemeRate).toBeUndefined()
      expect(result.data.fixedRewardEgoGifts).toBeUndefined()
    }
  })

  it('rejects invalid dungeonIdx', () => {
    const result = ThemePackDetailSchema.safeParse({
      exceptionConditions: [{ dungeonIdx: 5 }],
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
      themePackConfig: { textColor: 'af241c' },
      featuredBosses: [],
    })
    expect(result.success).toBe(false)
  })

  it('accepts populated featuredBosses with numeric and string portraitId', () => {
    const result = ThemePackDetailSchema.safeParse({
      exceptionConditions: [{ dungeonIdx: 0, selectableFloors: [0] }],
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
      themePackConfig: { textColor: 'af241c' },
      featuredBosses: [
        { unitId: 71001, portraitId: 91001 },
        { unitId: 1355, portraitId: '1355' },
      ],
    })
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.featuredBosses).toEqual([
        { unitId: 71001, portraitId: 91001 },
        { unitId: 1355, portraitId: '1355' },
      ])
    }
  })

  it('accepts empty featuredBosses', () => {
    const result = ThemePackDetailSchema.safeParse({
      exceptionConditions: [{ dungeonIdx: 0, selectableFloors: [0] }],
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
      themePackConfig: { textColor: 'af241c' },
      featuredBosses: [],
    })
    expect(result.success).toBe(true)
  })

  it('rejects detail with featuredBosses omitted', () => {
    const result = ThemePackDetailSchema.safeParse({
      exceptionConditions: [{ dungeonIdx: 0, selectableFloors: [0] }],
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

  it('accepts list entry with fixedRewardEgoGifts', () => {
    const result = ThemePackListSchema.safeParse({
      '3001': {
        exceptionConditions: [{ dungeonIdx: 1, selectableFloors: [2, 3, 4] }],
        specificEgoGiftPool: [9242],
        themePackConfig: { textColor: 'e5c6a0' },
        fixedRewardEgoGifts: [9242, 9083],
      },
    })
    expect(result.success).toBe(true)
  })
})

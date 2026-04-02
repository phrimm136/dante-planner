import { describe, it, expect } from 'vitest'
import {
  matchesDungeonDifficultyFilter,
  matchesFloorFilter,
  matchesEgoGiftFilter,
} from '../themePackFilter'
import { DUNGEON_IDX } from '../constants'
import type { ThemePackEntry } from '@/types/ThemePackTypes'

function makeEntry(overrides: Partial<ThemePackEntry> = {}): ThemePackEntry {
  return {
    exceptionConditions: [
      { dungeonIdx: DUNGEON_IDX.NORMAL, selectableFloors: [0, 1] },
      { dungeonIdx: DUNGEON_IDX.HARD, selectableFloors: [0] },
    ],
    specificEgoGiftPool: [9403, 9404],
    themePackConfig: { textColor: 'af241c' },
    ...overrides,
  }
}

describe('matchesDungeonDifficultyFilter', () => {
  it('returns true when no filter selected', () => {
    expect(matchesDungeonDifficultyFilter(makeEntry(), new Set())).toBe(true)
  })

  it('matches when pack has the selected dungeonIdx', () => {
    expect(matchesDungeonDifficultyFilter(makeEntry(), new Set([DUNGEON_IDX.NORMAL]))).toBe(true)
    expect(matchesDungeonDifficultyFilter(makeEntry(), new Set([DUNGEON_IDX.HARD]))).toBe(true)
  })

  it('does not match when pack lacks the selected dungeonIdx', () => {
    expect(matchesDungeonDifficultyFilter(makeEntry(), new Set([DUNGEON_IDX.EXTREME]))).toBe(false)
  })

  it('matches with AND logic — pack must have all selected difficulties', () => {
    expect(matchesDungeonDifficultyFilter(
      makeEntry(), new Set([DUNGEON_IDX.NORMAL, DUNGEON_IDX.HARD])
    )).toBe(true)
    expect(matchesDungeonDifficultyFilter(
      makeEntry(), new Set([DUNGEON_IDX.EXTREME, DUNGEON_IDX.NORMAL])
    )).toBe(false)
  })

  it('matches infinity (parallel) packs', () => {
    const infinityPack = makeEntry({
      exceptionConditions: [{ dungeonIdx: DUNGEON_IDX.PARALLEL }],
    })
    expect(matchesDungeonDifficultyFilter(infinityPack, new Set([DUNGEON_IDX.PARALLEL]))).toBe(true)
    expect(matchesDungeonDifficultyFilter(infinityPack, new Set([DUNGEON_IDX.NORMAL]))).toBe(false)
  })
})

describe('matchesFloorFilter', () => {
  it('returns true when no filter selected', () => {
    expect(matchesFloorFilter(makeEntry(), new Set())).toBe(true)
  })

  it('matches when pack has the selected floor', () => {
    expect(matchesFloorFilter(makeEntry(), new Set([0]))).toBe(true)
    expect(matchesFloorFilter(makeEntry(), new Set([1]))).toBe(true)
  })

  it('does not match when pack lacks the selected floor', () => {
    expect(matchesFloorFilter(makeEntry(), new Set([3]))).toBe(false)
  })

  it('does not match infinity packs with undefined selectableFloors', () => {
    const infinityPack = makeEntry({
      exceptionConditions: [{ dungeonIdx: DUNGEON_IDX.PARALLEL }],
    })
    expect(matchesFloorFilter(infinityPack, new Set([0]))).toBe(false)
  })

  it('matches with AND logic — pack must have all selected floors', () => {
    // Pack has floors 0 and 1 (Normal: [0,1], Hard: [0])
    expect(matchesFloorFilter(makeEntry(), new Set([0, 1]))).toBe(true)
    // Floor 4 not in pack
    expect(matchesFloorFilter(makeEntry(), new Set([0, 4]))).toBe(false)
  })
})

describe('matchesEgoGiftFilter', () => {
  it('returns true when no filter selected', () => {
    expect(matchesEgoGiftFilter(makeEntry(), new Set())).toBe(true)
  })

  it('matches when pack has the selected gift', () => {
    expect(matchesEgoGiftFilter(makeEntry(), new Set(['9403']))).toBe(true)
  })

  it('does not match when pack lacks the selected gift', () => {
    expect(matchesEgoGiftFilter(makeEntry(), new Set(['9999']))).toBe(false)
  })

  it('does not match for empty specificEgoGiftPool', () => {
    const emptyPack = makeEntry({ specificEgoGiftPool: [] })
    expect(matchesEgoGiftFilter(emptyPack, new Set(['9403']))).toBe(false)
  })

  it('converts number IDs to string for comparison', () => {
    expect(matchesEgoGiftFilter(makeEntry(), new Set(['9404']))).toBe(true)
  })

  it('matches gifts in fixedRewardEgoGifts', () => {
    const hiddenPack = makeEntry({
      specificEgoGiftPool: [],
      fixedRewardEgoGifts: [9242, 9083],
    })
    expect(matchesEgoGiftFilter(hiddenPack, new Set(['9242']))).toBe(true)
    expect(matchesEgoGiftFilter(hiddenPack, new Set(['9083']))).toBe(true)
  })

  it('does not match when gift is in neither pool', () => {
    const hiddenPack = makeEntry({
      specificEgoGiftPool: [9403],
      fixedRewardEgoGifts: [9242],
    })
    expect(matchesEgoGiftFilter(hiddenPack, new Set(['9999']))).toBe(false)
  })

  it('matches across both specificEgoGiftPool and fixedRewardEgoGifts', () => {
    const pack = makeEntry({
      specificEgoGiftPool: [9403],
      fixedRewardEgoGifts: [9242],
    })
    expect(matchesEgoGiftFilter(pack, new Set(['9403']))).toBe(true)
    expect(matchesEgoGiftFilter(pack, new Set(['9242']))).toBe(true)
  })
})

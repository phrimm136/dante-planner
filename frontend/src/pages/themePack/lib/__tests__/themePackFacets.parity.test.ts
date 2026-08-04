/**
 * themePackFacets.parity.test.ts
 *
 * Pins THEME_PACK_FACETS to the per-facet predicates ThemePackList called
 * before the facet migration, over the full cartesian product of selections.
 */

import { describe, it, expect } from 'vitest'
import { applyFacets } from '@/shared/filter'
import { DUNGEON_IDX, type DungeonIdx, type ThemePackFloor } from '@/shared/gameData'
import { enumerateSelectionStates, findParityMismatches } from '@/test-utils/facetParity'
import { THEME_PACK_FACETS, type ThemePackFacetState } from '../themePackFilter'
import type { ThemePackEntry } from '../../types/ThemePackTypes'

function legacyDungeonDifficulty(
  entry: ThemePackEntry,
  selectedDifficulties: ReadonlySet<DungeonIdx>,
): boolean {
  if (selectedDifficulties.size === 0) return true
  const packDifficulties = new Set(entry.exceptionConditions.map((c) => c.dungeonIdx))
  return Array.from(selectedDifficulties).every((d) => packDifficulties.has(d))
}

function legacyFloor(entry: ThemePackEntry, selectedFloors: ReadonlySet<ThemePackFloor>): boolean {
  if (selectedFloors.size === 0) return true
  const packFloors = new Set<number>()
  for (const cond of entry.exceptionConditions) {
    for (const f of cond.selectableFloors ?? []) {
      packFloors.add(f)
    }
  }
  return Array.from(selectedFloors).every((f) => packFloors.has(f))
}

function legacyEgoGift(entry: ThemePackEntry, selectedEgoGifts: ReadonlySet<string>): boolean {
  if (selectedEgoGifts.size === 0) return true
  if (entry.specificEgoGiftPool.some((giftId) => selectedEgoGifts.has(String(giftId)))) return true
  if (entry.fixedRewardEgoGifts?.some((giftId) => selectedEgoGifts.has(String(giftId)))) return true
  return false
}

interface PackFixture {
  id: string
  entry: ThemePackEntry
}

function legacyMatches(pack: PackFixture, state: ThemePackFacetState): boolean {
  if (!legacyDungeonDifficulty(pack.entry, state.selectedDifficulties)) return false
  if (!legacyFloor(pack.entry, state.selectedFloors)) return false
  if (!legacyEgoGift(pack.entry, state.selectedEgoGifts)) return false
  return true
}

function makePack(id: string, overrides: Partial<ThemePackEntry> = {}): PackFixture {
  return {
    id,
    entry: {
      exceptionConditions: [],
      specificEgoGiftPool: [],
      themePackConfig: { textColor: 'af241c' },
      ...overrides,
    },
  }
}

const ITEMS: PackFixture[] = [
  makePack('1001', {
    exceptionConditions: [
      { dungeonIdx: DUNGEON_IDX.NORMAL, selectableFloors: [0, 1] },
      { dungeonIdx: DUNGEON_IDX.HARD, selectableFloors: [0] },
    ],
    specificEgoGiftPool: [9403, 9404],
  }),
  makePack('1002', {
    exceptionConditions: [{ dungeonIdx: DUNGEON_IDX.NORMAL, selectableFloors: [1] }],
    specificEgoGiftPool: [9403],
  }),
  makePack('1003', {
    exceptionConditions: [{ dungeonIdx: DUNGEON_IDX.PARALLEL }],
    specificEgoGiftPool: [],
    fixedRewardEgoGifts: [9242, 9083],
  }),
  makePack('1004', {
    exceptionConditions: [{ dungeonIdx: DUNGEON_IDX.EXTREME }],
    specificEgoGiftPool: [9404],
    fixedRewardEgoGifts: [],
  }),
  makePack('1005', {
    exceptionConditions: [
      { dungeonIdx: DUNGEON_IDX.NORMAL, selectableFloors: [0, 1, 2] },
      { dungeonIdx: DUNGEON_IDX.HARD, selectableFloors: [1, 2] },
      { dungeonIdx: DUNGEON_IDX.EXTREME },
    ],
    specificEgoGiftPool: [9403],
    fixedRewardEgoGifts: [9242],
  }),
  makePack('1006'),
]

const BASE_STATE: ThemePackFacetState = {
  selectedDifficulties: new Set(),
  selectedFloors: new Set(),
  selectedEgoGifts: new Set(),
}

const STATES = enumerateSelectionStates(BASE_STATE, {
  selectedDifficulties: [
    [],
    [DUNGEON_IDX.NORMAL],
    [DUNGEON_IDX.HARD],
    [DUNGEON_IDX.NORMAL, DUNGEON_IDX.HARD],
    [DUNGEON_IDX.PARALLEL],
    [DUNGEON_IDX.NORMAL, DUNGEON_IDX.EXTREME],
  ],
  selectedFloors: [[], [0], [1], [0, 1], [2], [1, 2]],
  selectedEgoGifts: [[], ['9403'], ['9404'], ['9403', '9404'], ['9242'], ['9083', '9404']],
})

describe('THEME_PACK_FACETS parity', () => {
  it('enumerates every selection combination', () => {
    expect(STATES.length).toBe(6 ** 3)
  })

  it('survives the same ids as the pre-migration predicates', () => {
    const mismatches = findParityMismatches(
      ITEMS,
      STATES,
      (item, state) => legacyMatches(item, state),
      (item, state) => applyFacets(item.entry, state, THEME_PACK_FACETS),
    )
    expect(mismatches).toEqual([])
  })

  it('requires ALL selected difficulties and floors', () => {
    const bothDifficulties: ThemePackFacetState = {
      ...BASE_STATE,
      selectedDifficulties: new Set<DungeonIdx>([DUNGEON_IDX.NORMAL, DUNGEON_IDX.HARD]),
    }
    const byDifficulty = ITEMS.filter((item) =>
      applyFacets(item.entry, bothDifficulties, THEME_PACK_FACETS),
    )
    expect(byDifficulty.map((item) => item.id)).toEqual(['1001', '1005'])

    const bothFloors: ThemePackFacetState = {
      ...BASE_STATE,
      selectedFloors: new Set<ThemePackFloor>([0, 1]),
    }
    const byFloor = ITEMS.filter((item) => applyFacets(item.entry, bothFloors, THEME_PACK_FACETS))
    expect(byFloor.map((item) => item.id)).toEqual(['1001', '1005'])
  })

  it('matches ego gifts across both pools', () => {
    const fixedReward: ThemePackFacetState = { ...BASE_STATE, selectedEgoGifts: new Set(['9242']) }
    const survivors = ITEMS.filter((item) =>
      applyFacets(item.entry, fixedReward, THEME_PACK_FACETS),
    )
    expect(survivors.map((item) => item.id)).toEqual(['1003', '1005'])
  })
})

/**
 * plannerRules.test.ts
 *
 * Unit tests for planner domain predicates: EGO Gift theme-pack affordability.
 */

import { describe, it, expect } from 'vitest'
import {
  canSelectFloorThemePack,
  isGiftAffordableForThemePack,
  getUnaffordableGiftIds,
  offeredFloorDifficulties,
  usedFloorThemePackIds,
} from '../plannerRules'
import { DUNGEON_IDX, ThemePackIdSchema } from '@/shared/gameData'
import type { EncodedGiftId, ThemePackId } from '@/shared/gameData'
import type { FloorThemeSelection } from '@/pages/themePack'
import type { EGOGiftSpec } from '@/pages/egoGift'
import { asEncodedGiftId } from '@/test-utils/fixtures'

const ENCODED_9220 = asEncodedGiftId('9220')
const ENCODED_19220 = asEncodedGiftId('19220')
const ENCODED_9999 = asEncodedGiftId('9999')

// ============================================================================
// Fixtures
// ============================================================================

/** Builds a genuinely valid EGOGiftSpec restricted to the given theme packs. */
function makeGiftSpec(themePack: string[]): EGOGiftSpec {
  return {
    tag: ['TIER_1'],
    keyword: null,
    battleKeywordList: [],
    attributeType: '',
    themePack,
    maxEnhancement: 0,
  }
}

// ============================================================================
// isGiftAffordableForThemePack
// ============================================================================

describe('isGiftAffordableForThemePack', () => {
  it('universal gift (empty themePack) is available in any theme pack', () => {
    const gift = makeGiftSpec([])
    expect(isGiftAffordableForThemePack(gift, '1024')).toBe(true)
    expect(isGiftAffordableForThemePack(gift, '1110')).toBe(true)
  })

  it('restricted gift is available only for its listed pack', () => {
    const gift = makeGiftSpec(['1024'])
    expect(isGiftAffordableForThemePack(gift, '1024')).toBe(true)
    expect(isGiftAffordableForThemePack(gift, '1110')).toBe(false)
  })
})

// ============================================================================
// getUnaffordableGiftIds
// ============================================================================

describe('getUnaffordableGiftIds', () => {
  const spec: Record<string, EGOGiftSpec> = {
    '9220': makeGiftSpec(['1024']),
  }

  it('base gift ID on wrong pack returns that ID', () => {
    const result = getUnaffordableGiftIds(new Set([ENCODED_9220]), '1110', spec)
    expect(result).toEqual(['9220'])
  })

  it('enhanced gift ID (19220) strips prefix to look up base ID 9220', () => {
    // '19220' → getBaseGiftId → '9220' → not in '1110' → unaffordable
    const result = getUnaffordableGiftIds(new Set([ENCODED_19220]), '1110', spec)
    expect(result).toEqual(['19220'])
  })

  it('gift on correct pack returns empty array', () => {
    const result = getUnaffordableGiftIds(new Set([ENCODED_9220]), '1024', spec)
    expect(result).toEqual([])
  })

  it('gift not in spec is silently skipped', () => {
    const result = getUnaffordableGiftIds(new Set([ENCODED_9999]), '1110', spec)
    expect(result).toEqual([])
  })
})

// ============================================================================
// Floor selection rules the editor offers
// ============================================================================

function floorsWith(packs: (ThemePackId | '' | null)[]): FloorThemeSelection[] {
  return packs.map((themePackId) => ({
    themePackId: themePackId as ThemePackId | null,
    difficulty: DUNGEON_IDX.HARD,
    giftIds: new Set<EncodedGiftId>(),
  }))
}

describe('offeredFloorDifficulties', () => {
  it('offers HARD only on floor 1 of a 10F planner', () => {
    expect(offeredFloorDifficulties('10F', 0, true)).toEqual([DUNGEON_IDX.HARD])
  })

  it('offers NORMAL and HARD on floor 1 of a 5F planner', () => {
    expect(offeredFloorDifficulties('5F', 0, true)).toEqual([DUNGEON_IDX.NORMAL, DUNGEON_IDX.HARD])
  })

  it('offers HARD only after a HARD floor on a 5F planner', () => {
    expect(offeredFloorDifficulties('5F', 1, false)).toEqual([DUNGEON_IDX.HARD])
  })

  it('offers EXTREME only on floor 11 of a 15F planner', () => {
    expect(offeredFloorDifficulties('15F', 10, false)).toEqual([DUNGEON_IDX.EXTREME])
  })

  it('offers nothing past the category floor count', () => {
    expect(offeredFloorDifficulties('5F', 5, true)).toEqual([])
  })
})

describe('canSelectFloorThemePack', () => {
  it('treats an empty-string pack on the previous floor as not chosen', () => {
    expect(canSelectFloorThemePack(1, floorsWith(['']))).toBe(false)
  })

  it('allows a floor whose previous floor holds a pack', () => {
    expect(canSelectFloorThemePack(1, floorsWith([ThemePackIdSchema.parse('1001')]))).toBe(true)
  })
})

describe('usedFloorThemePackIds', () => {
  it('ignores packs on floors past the category count', () => {
    const floors = floorsWith([
      ThemePackIdSchema.parse('1001'),
      null,
      null,
      null,
      null,
      ThemePackIdSchema.parse('1006'),
    ])
    expect(usedFloorThemePackIds(floors, 1, 5)).toEqual(['1001'])
  })

  it('excludes the floor being edited', () => {
    const floors = floorsWith([ThemePackIdSchema.parse('1001'), ThemePackIdSchema.parse('1002')])
    expect(usedFloorThemePackIds(floors, 0, 5)).toEqual(['1002'])
  })
})

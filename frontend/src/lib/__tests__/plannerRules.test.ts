/**
 * plannerRules.test.ts
 *
 * Unit tests for planner domain predicates: EGO Gift theme-pack affordability.
 */

import { describe, it, expect } from 'vitest'
import { isGiftAffordableForThemePack, getUnaffordableGiftIds } from '../plannerRules'
import type { EGOGiftSpec } from '@/types/EGOGiftTypes'

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
    const result = getUnaffordableGiftIds(new Set(['9220']), '1110', spec)
    expect(result).toEqual(['9220'])
  })

  it('enhanced gift ID (19220) strips prefix to look up base ID 9220', () => {
    // '19220' → getBaseGiftId → '9220' → not in '1110' → unaffordable
    const result = getUnaffordableGiftIds(new Set(['19220']), '1110', spec)
    expect(result).toEqual(['19220'])
  })

  it('gift on correct pack returns empty array', () => {
    const result = getUnaffordableGiftIds(new Set(['9220']), '1024', spec)
    expect(result).toEqual([])
  })

  it('gift not in spec is silently skipped', () => {
    const result = getUnaffordableGiftIds(new Set(['9999']), '1110', spec)
    expect(result).toEqual([])
  })
})

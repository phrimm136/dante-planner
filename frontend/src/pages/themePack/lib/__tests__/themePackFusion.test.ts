/**
 * themePackFusion.test.ts
 *
 * Unit tests for fusion-gift discovery over a theme pack's gift pool.
 */

import { describe, it, expect } from 'vitest'

import { findFusionGifts } from '../themePackFusion'
import type { EGOGiftSpec } from '@/pages/egoGift'
import { EGOGiftRecipeSchema } from '@/pages/egoGift'

function makeSpec(overrides: Partial<EGOGiftSpec> = {}): EGOGiftSpec {
  return {
    tag: ['TIER_3'],
    keyword: null,
    battleKeywordList: [],
    attributeType: 'CRIMSON',
    themePack: [],
    maxEnhancement: 0,
    ...overrides,
  }
}

const POOL = ['1', '2', '3']

const SPEC: Record<string, EGOGiftSpec> = {
  '1': makeSpec(),
  '2': makeSpec(),
  '3': makeSpec(),
  // Every material of one set is in the pool
  '10': makeSpec({ recipe: EGOGiftRecipeSchema.parse({ materials: [[1, 2]] }) }),
  // Only the second set is covered
  '11': makeSpec({
    recipe: EGOGiftRecipeSchema.parse({
      materials: [
        [1, 9],
        [2, 3],
      ],
    }),
  }),
  // No set is covered
  '12': makeSpec({ recipe: EGOGiftRecipeSchema.parse({ materials: [[1, 9]] }) }),
  // An empty material set never counts as covered
  '13': makeSpec({ recipe: EGOGiftRecipeSchema.parse({ materials: [[]] }) }),
  // Both mixed pools are covered
  '14': makeSpec({
    recipe: EGOGiftRecipeSchema.parse({ type: 'mixed', a: { ids: [1, 2], count: 1 }, b: { ids: [3], count: 1 } }),
  }),
  // One id of the mixed recipe sits outside the pool
  '15': makeSpec({
    recipe: EGOGiftRecipeSchema.parse({ type: 'mixed', a: { ids: [1, 2], count: 1 }, b: { ids: [9], count: 1 } }),
  }),
  // Empty mixed pools never count as covered
  '16': makeSpec({ recipe: EGOGiftRecipeSchema.parse({ type: 'mixed', a: { ids: [], count: 0 }, b: { ids: [], count: 0 } }) }),
  // Recipeless gifts are never fusion results
  '17': makeSpec(),
}

describe('findFusionGifts', () => {
  const cases: { name: string; giftIds: string[]; expected: string[] }[] = [
    {
      name: 'returns every gift whose recipe the pool covers',
      giftIds: POOL,
      expected: ['10', '11', '14'],
    },
    { name: 'finds nothing for an empty pool', giftIds: [], expected: [] },
    {
      name: 'needs the whole material set, not part of it',
      giftIds: ['1'],
      expected: [],
    },
    {
      name: 'covers a standard recipe from a partial pool that still holds one full set',
      giftIds: ['1', '2'],
      expected: ['10'],
    },
  ]

  it.each(cases)('$name', ({ giftIds, expected }) => {
    expect(findFusionGifts(SPEC, giftIds)).toEqual(expected)
  })

  it('skips gifts already in the pool', () => {
    expect(findFusionGifts(SPEC, [...POOL, '10'])).toEqual(['11', '14'])
  })

  it('reports a gift once even when several material sets are covered', () => {
    const spec: Record<string, EGOGiftSpec> = {
      '20': makeSpec({ recipe: EGOGiftRecipeSchema.parse({ materials: [[1], [2]] }) }),
    }

    expect(findFusionGifts(spec, POOL)).toEqual(['20'])
  })
})

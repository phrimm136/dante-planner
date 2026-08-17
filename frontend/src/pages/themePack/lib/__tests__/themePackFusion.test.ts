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

const POOL = ['9001', '9002', '9003']

const SPEC: Record<string, EGOGiftSpec> = {
  '9001': makeSpec(),
  '9002': makeSpec(),
  '9003': makeSpec(),
  // Every material of one set is in the pool
  '9010': makeSpec({ recipe: EGOGiftRecipeSchema.parse({ materials: [['9001', '9002']] }) }),
  // Only the second set is covered
  '9011': makeSpec({
    recipe: EGOGiftRecipeSchema.parse({
      materials: [
        ['9001', '9009'],
        ['9002', '9003'],
      ],
    }),
  }),
  // No set is covered
  '9012': makeSpec({ recipe: EGOGiftRecipeSchema.parse({ materials: [['9001', '9009']] }) }),
  // An empty material set never counts as covered
  '9013': makeSpec({ recipe: EGOGiftRecipeSchema.parse({ materials: [[]] }) }),
  // Both mixed pools are covered
  '9014': makeSpec({
    recipe: EGOGiftRecipeSchema.parse({
      type: 'mixed',
      a: { ids: ['9001', '9002'], count: 1 },
      b: { ids: ['9003'], count: 1 },
    }),
  }),
  // One id of the mixed recipe sits outside the pool
  '9015': makeSpec({
    recipe: EGOGiftRecipeSchema.parse({
      type: 'mixed',
      a: { ids: ['9001', '9002'], count: 1 },
      b: { ids: ['9009'], count: 1 },
    }),
  }),
  // Empty mixed pools never count as covered
  '9016': makeSpec({
    recipe: EGOGiftRecipeSchema.parse({
      type: 'mixed',
      a: { ids: [], count: 0 },
      b: { ids: [], count: 0 },
    }),
  }),
  // Recipeless gifts are never fusion results
  '9017': makeSpec(),
}

describe('findFusionGifts', () => {
  const cases: { name: string; giftIds: string[]; expected: string[] }[] = [
    {
      name: 'returns every gift whose recipe the pool covers',
      giftIds: POOL,
      expected: ['9010', '9011', '9014'],
    },
    { name: 'finds nothing for an empty pool', giftIds: [], expected: [] },
    {
      name: 'needs the whole material set, not part of it',
      giftIds: ['9001'],
      expected: [],
    },
    {
      name: 'covers a standard recipe from a partial pool that still holds one full set',
      giftIds: ['9001', '9002'],
      expected: ['9010'],
    },
  ]

  it.each(cases)('$name', ({ giftIds, expected }) => {
    expect(findFusionGifts(SPEC, giftIds)).toEqual(expected)
  })

  it('skips gifts already in the pool', () => {
    expect(findFusionGifts(SPEC, [...POOL, '9010'])).toEqual(['9011', '9014'])
  })

  it('reports a gift once even when several material sets are covered', () => {
    const spec: Record<string, EGOGiftSpec> = {
      '9020': makeSpec({ recipe: EGOGiftRecipeSchema.parse({ materials: [['9001'], ['9002']] }) }),
    }

    expect(findFusionGifts(spec, POOL)).toEqual(['9020'])
  })
})

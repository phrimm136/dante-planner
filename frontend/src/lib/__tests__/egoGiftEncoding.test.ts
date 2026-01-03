/**
 * egoGiftEncoding.test.ts
 *
 * Unit tests for EGO Gift encoding and cascade selection utilities.
 * Tests encoding/decoding, selection lookup, and recipe ingredient extraction.
 */

import { describe, it, expect } from 'vitest'
import {
  encodeGiftSelection,
  decodeGiftSelection,
  getBaseGiftId,
  isGiftSelected,
  getGiftEnhancement,
  findEncodedGiftId,
  buildSelectionLookup,
  getCascadeIngredients,
} from '../egoGiftEncoding'
import type { EGOGiftRecipe } from '@/types/EGOGiftTypes'

describe('encodeGiftSelection', () => {
  it('returns just giftId when enhancement is 0', () => {
    expect(encodeGiftSelection(0, '9001')).toBe('9001')
  })

  it('prefixes with 1 when enhancement is 1', () => {
    expect(encodeGiftSelection(1, '9001')).toBe('19001')
  })

  it('prefixes with 2 when enhancement is 2', () => {
    expect(encodeGiftSelection(2, '9001')).toBe('29001')
  })
})

describe('decodeGiftSelection', () => {
  it('decodes 4-digit ID as enhancement 0', () => {
    const result = decodeGiftSelection('9001')
    expect(result).toEqual({ enhancement: 0, giftId: '9001' })
  })

  it('decodes 5-digit ID starting with 1 as enhancement 1', () => {
    const result = decodeGiftSelection('19001')
    expect(result).toEqual({ enhancement: 1, giftId: '9001' })
  })

  it('decodes 5-digit ID starting with 2 as enhancement 2', () => {
    const result = decodeGiftSelection('29001')
    expect(result).toEqual({ enhancement: 2, giftId: '9001' })
  })

  it('falls back to enhancement 0 for invalid prefix', () => {
    const result = decodeGiftSelection('39001')
    expect(result).toEqual({ enhancement: 0, giftId: '39001' })
  })
})

describe('getBaseGiftId', () => {
  it('extracts giftId from base selection', () => {
    expect(getBaseGiftId('9001')).toBe('9001')
  })

  it('extracts giftId from enhanced selection', () => {
    expect(getBaseGiftId('19001')).toBe('9001')
    expect(getBaseGiftId('29001')).toBe('9001')
  })
})

describe('isGiftSelected', () => {
  it('returns true when base gift is in selection', () => {
    const selection = new Set(['9001', '9002'])
    expect(isGiftSelected('9001', selection)).toBe(true)
  })

  it('returns true when enhanced gift is in selection', () => {
    const selection = new Set(['19001', '9002'])
    expect(isGiftSelected('9001', selection)).toBe(true)
  })

  it('returns false when gift is not in selection', () => {
    const selection = new Set(['9002', '9003'])
    expect(isGiftSelected('9001', selection)).toBe(false)
  })

  it('returns false for empty selection', () => {
    expect(isGiftSelected('9001', new Set())).toBe(false)
  })
})

describe('getGiftEnhancement', () => {
  it('returns 0 when gift is not selected', () => {
    expect(getGiftEnhancement('9001', new Set())).toBe(0)
  })

  it('returns 0 when gift is selected at base level', () => {
    expect(getGiftEnhancement('9001', new Set(['9001']))).toBe(0)
  })

  it('returns 1 when gift is selected at enhancement 1', () => {
    expect(getGiftEnhancement('9001', new Set(['19001']))).toBe(1)
  })

  it('returns 2 when gift is selected at enhancement 2', () => {
    expect(getGiftEnhancement('9001', new Set(['29001']))).toBe(2)
  })
})

describe('findEncodedGiftId', () => {
  it('returns encoded ID when gift is found', () => {
    const selection = new Set(['19001', '9002'])
    expect(findEncodedGiftId('9001', selection)).toBe('19001')
  })

  it('returns undefined when gift is not found', () => {
    const selection = new Set(['9002', '9003'])
    expect(findEncodedGiftId('9001', selection)).toBeUndefined()
  })
})

describe('buildSelectionLookup', () => {
  it('builds map from encoded selections', () => {
    const selection = new Set(['9001', '19002', '29003'])
    const lookup = buildSelectionLookup(selection)

    expect(lookup.get('9001')).toEqual({ encodedId: '9001', enhancement: 0 })
    expect(lookup.get('9002')).toEqual({ encodedId: '19002', enhancement: 1 })
    expect(lookup.get('9003')).toEqual({ encodedId: '29003', enhancement: 2 })
  })

  it('returns empty map for empty selection', () => {
    const lookup = buildSelectionLookup(new Set())
    expect(lookup.size).toBe(0)
  })
})

describe('getCascadeIngredients', () => {
  describe('standard recipes', () => {
    it('returns empty array when recipe is undefined', () => {
      expect(getCascadeIngredients(undefined)).toEqual([])
    })

    it('returns ingredients from single recipe option', () => {
      const recipe: EGOGiftRecipe = {
        materials: [[9069, 9099, 9182]],
      }
      const result = getCascadeIngredients(recipe)
      expect(result).toEqual(expect.arrayContaining([9069, 9099, 9182]))
      expect(result).toHaveLength(3)
    })

    it('returns union of ingredients from multiple recipe options', () => {
      // Gift 9088 has two recipe options with overlapping ingredients
      const recipe: EGOGiftRecipe = {
        materials: [
          [9003, 9053, 9157],
          [9003, 9053, 9101, 9155],
        ],
      }
      const result = getCascadeIngredients(recipe)
      // Union: 9003, 9053, 9157, 9101, 9155 (5 unique)
      expect(result).toEqual(expect.arrayContaining([9003, 9053, 9157, 9101, 9155]))
      expect(result).toHaveLength(5)
    })

    it('deduplicates shared ingredients across recipe options', () => {
      const recipe: EGOGiftRecipe = {
        materials: [
          [9003, 9053],
          [9003, 9053, 9101],
        ],
      }
      const result = getCascadeIngredients(recipe)
      // 9003 and 9053 appear in both options but should only be returned once
      expect(result.filter((id) => id === 9003)).toHaveLength(1)
      expect(result.filter((id) => id === 9053)).toHaveLength(1)
    })

    it('handles empty materials array', () => {
      const recipe: EGOGiftRecipe = {
        materials: [],
      }
      expect(getCascadeIngredients(recipe)).toEqual([])
    })

    it('handles recipe option with empty ingredient array', () => {
      const recipe: EGOGiftRecipe = {
        materials: [[], [9001, 9002]],
      }
      const result = getCascadeIngredients(recipe)
      expect(result).toEqual(expect.arrayContaining([9001, 9002]))
      expect(result).toHaveLength(2)
    })
  })

  describe('mixed recipes (Lunar Memory)', () => {
    it('returns empty array for mixed recipe type', () => {
      // Lunar Memory (9083) - requires manual selection
      const recipe: EGOGiftRecipe = {
        type: 'mixed',
        a: { ids: [9105, 9110, 9116, 9121, 9126, 9131, 9136], count: 2 },
        b: { ids: [9142, 9147, 9152], count: 3 },
      }
      expect(getCascadeIngredients(recipe)).toEqual([])
    })

    it('does not auto-select any ingredients from mixed pools', () => {
      const recipe: EGOGiftRecipe = {
        type: 'mixed',
        a: { ids: [9105], count: 1 },
        b: { ids: [9142], count: 1 },
      }
      const result = getCascadeIngredients(recipe)
      expect(result).not.toContain(9105)
      expect(result).not.toContain(9142)
    })
  })

  describe('real game data examples', () => {
    it('handles gift 9100 (single recipe, 3 ingredients)', () => {
      const recipe: EGOGiftRecipe = {
        materials: [[9069, 9099, 9182]],
      }
      const result = getCascadeIngredients(recipe)
      expect(result.sort()).toEqual([9069, 9099, 9182])
    })

    it('handles gift 9088 (two recipe options)', () => {
      const recipe: EGOGiftRecipe = {
        materials: [
          [9003, 9053, 9157],
          [9003, 9053, 9101, 9155],
        ],
      }
      const result = getCascadeIngredients(recipe)
      expect(result.sort()).toEqual([9003, 9053, 9101, 9155, 9157])
    })

    it('handles gift 9761 (hardOnly, single recipe)', () => {
      const recipe: EGOGiftRecipe = {
        materials: [[9759, 9760]],
      }
      const result = getCascadeIngredients(recipe)
      expect(result.sort()).toEqual([9759, 9760])
    })
  })
})

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
  findEncodedGiftId,
  buildSelectionLookup,
  getCascadeIngredients,
} from '../egoGiftEncoding'
import type { EGOGiftRecipe } from '@/pages/egoGift'
import { EGOGiftRecipeSchema } from '@/pages/egoGift'
import { EncodedGiftIdSchema } from '@/shared/gameData'
import { asEGOGiftId, asEncodedGiftId } from '@/test-utils/fixtures'

const GIFT_9001 = asEGOGiftId('9001')
const GIFT_9002 = asEGOGiftId('9002')
const GIFT_9003 = asEGOGiftId('9003')
const ENCODED_9001 = asEncodedGiftId('9001')
const ENCODED_9002 = asEncodedGiftId('9002')
const ENCODED_9003 = asEncodedGiftId('9003')
const ENCODED_19001 = asEncodedGiftId('19001')
const ENCODED_19002 = asEncodedGiftId('19002')
const ENCODED_29001 = asEncodedGiftId('29001')
const ENCODED_29003 = asEncodedGiftId('29003')

describe('encodeGiftSelection', () => {
  it('returns just giftId when enhancement is 0', () => {
    expect(encodeGiftSelection(0, GIFT_9001)).toBe('9001')
  })

  it('prefixes with 1 when enhancement is 1', () => {
    expect(encodeGiftSelection(1, GIFT_9001)).toBe('19001')
  })

  it('prefixes with 2 when enhancement is 2', () => {
    expect(encodeGiftSelection(2, GIFT_9001)).toBe('29001')
  })
})

describe('decodeGiftSelection', () => {
  it('decodes 4-digit ID as enhancement 0', () => {
    expect(decodeGiftSelection(ENCODED_9001)).toEqual({ enhancement: 0, giftId: '9001' })
  })

  it('decodes 5-digit ID starting with 1 as enhancement 1', () => {
    expect(decodeGiftSelection(ENCODED_19001)).toEqual({ enhancement: 1, giftId: '9001' })
  })

  it('decodes 5-digit ID starting with 2 as enhancement 2', () => {
    expect(decodeGiftSelection(ENCODED_29001)).toEqual({ enhancement: 2, giftId: '9001' })
  })
})

describe('getBaseGiftId', () => {
  it('extracts giftId from base selection', () => {
    expect(getBaseGiftId(ENCODED_9001)).toBe('9001')
  })

  it('extracts giftId from enhanced selection', () => {
    expect(getBaseGiftId(ENCODED_19001)).toBe('9001')
    expect(getBaseGiftId(ENCODED_29001)).toBe('9001')
  })
})

describe('findEncodedGiftId', () => {
  it('returns encoded ID when gift is found', () => {
    const selection = new Set([ENCODED_19001, ENCODED_9002])
    expect(findEncodedGiftId(GIFT_9001, selection)).toBe('19001')
  })

  it('returns undefined when gift is not found', () => {
    const selection = new Set([ENCODED_9002, ENCODED_9003])
    expect(findEncodedGiftId(GIFT_9001, selection)).toBeUndefined()
  })
})

describe('buildSelectionLookup', () => {
  it('builds map from encoded selections', () => {
    const selection = new Set([ENCODED_9001, ENCODED_19002, ENCODED_29003])
    const lookup = buildSelectionLookup(selection)

    expect(lookup.get(GIFT_9001)).toEqual({ encodedId: '9001', enhancement: 0 })
    expect(lookup.get(GIFT_9002)).toEqual({ encodedId: '19002', enhancement: 1 })
    expect(lookup.get(GIFT_9003)).toEqual({ encodedId: '29003', enhancement: 2 })
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
      const recipe: EGOGiftRecipe = EGOGiftRecipeSchema.parse({
        materials: [['9069', '9099', '9182']],
      })
      const result = getCascadeIngredients(recipe)
      expect(result).toEqual(expect.arrayContaining(['9069', '9099', '9182']))
      expect(result).toHaveLength(3)
    })

    it('returns union of ingredients from multiple recipe options', () => {
      // Gift 9088 has two recipe options with overlapping ingredients
      const recipe: EGOGiftRecipe = EGOGiftRecipeSchema.parse({
        materials: [
          ['9003', '9053', '9157'],
          ['9003', '9053', '9101', '9155'],
        ],
      })
      const result = getCascadeIngredients(recipe)
      // Union: '9003', '9053', '9157', '9101', '9155' (5 unique)
      expect(result).toEqual(expect.arrayContaining(['9003', '9053', '9157', '9101', '9155']))
      expect(result).toHaveLength(5)
    })

    it('deduplicates shared ingredients across recipe options', () => {
      const recipe: EGOGiftRecipe = EGOGiftRecipeSchema.parse({
        materials: [
          ['9003', '9053'],
          ['9003', '9053', '9101'],
        ],
      })
      const result = getCascadeIngredients(recipe)
      // '9003' and '9053' appear in both options but should only be returned once
      expect(result.filter((id) => id === '9003')).toHaveLength(1)
      expect(result.filter((id) => id === '9053')).toHaveLength(1)
    })

    it('handles empty materials array', () => {
      const recipe: EGOGiftRecipe = EGOGiftRecipeSchema.parse({
        materials: [],
      })
      expect(getCascadeIngredients(recipe)).toEqual([])
    })

    it('handles recipe option with empty ingredient array', () => {
      const recipe: EGOGiftRecipe = EGOGiftRecipeSchema.parse({
        materials: [[], ['9001', '9002']],
      })
      const result = getCascadeIngredients(recipe)
      expect(result).toEqual(expect.arrayContaining(['9001', '9002']))
      expect(result).toHaveLength(2)
    })
  })

  describe('mixed recipes (Lunar Memory)', () => {
    it('returns empty array for mixed recipe type', () => {
      // Lunar Memory (9083) - requires manual selection
      const recipe: EGOGiftRecipe = EGOGiftRecipeSchema.parse({
        type: 'mixed',
        a: { ids: ['9105', '9110', '9116', '9121', '9126', '9131', '9136'], count: 2 },
        b: { ids: ['9142', '9147', '9152'], count: 3 },
      })
      expect(getCascadeIngredients(recipe)).toEqual([])
    })

    it('does not auto-select any ingredients from mixed pools', () => {
      const recipe: EGOGiftRecipe = EGOGiftRecipeSchema.parse({
        type: 'mixed',
        a: { ids: ['9105'], count: 1 },
        b: { ids: ['9142'], count: 1 },
      })
      const result = getCascadeIngredients(recipe)
      expect(result).not.toContain('9105')
      expect(result).not.toContain('9142')
    })
  })

  describe('real game data examples', () => {
    it('handles gift 9100 (single recipe, 3 ingredients)', () => {
      const recipe: EGOGiftRecipe = EGOGiftRecipeSchema.parse({
        materials: [['9069', '9099', '9182']],
      })
      const result = getCascadeIngredients(recipe)
      expect(result.sort()).toEqual(['9069', '9099', '9182'])
    })

    it('handles gift 9088 (two recipe options)', () => {
      const recipe: EGOGiftRecipe = EGOGiftRecipeSchema.parse({
        materials: [
          ['9003', '9053', '9157'],
          ['9003', '9053', '9101', '9155'],
        ],
      })
      const result = getCascadeIngredients(recipe)
      expect(result.sort()).toEqual(['9003', '9053', '9101', '9155', '9157'])
    })

    it('handles gift 9761 (hardOnly, single recipe)', () => {
      const recipe: EGOGiftRecipe = EGOGiftRecipeSchema.parse({
        materials: [['9759', '9760']],
      })
      const result = getCascadeIngredients(recipe)
      expect(result.sort()).toEqual(['9759', '9760'])
    })
  })
})

describe('decodeGiftSelection over the EncodedGiftIdSchema domain', () => {
  // Every optional single-digit prefix against every four-digit body, filtered to
  // what the schema accepts, so widening EncodedGiftIdSchema past what the decoder
  // handles fails here instead of stranding the new ids.
  function candidateIds(): string[] {
    const ids: string[] = []
    for (const prefix of ['', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9']) {
      for (let body = 0; body < 10000; body++) {
        ids.push(`${prefix}${String(body).padStart(4, '0')}`)
      }
    }
    return ids
  }

  const accepted = candidateIds().flatMap((id) => {
    const parsed = EncodedGiftIdSchema.safeParse(id)
    return parsed.success ? [parsed.data] : []
  })

  it('round-trips every schema-valid gift id', () => {
    // '9000'-'9999', plus the same bodies behind each of the two permitted prefixes.
    expect(accepted).toHaveLength(3000)
    const roundTripFailures = accepted.filter((id) => {
      const decoded = decodeGiftSelection(id)
      return encodeGiftSelection(decoded.enhancement, decoded.giftId) !== id
    })
    expect(roundTripFailures).toEqual([])
  })
})

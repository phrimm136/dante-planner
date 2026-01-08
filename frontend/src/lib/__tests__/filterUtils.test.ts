/**
 * filterUtils.test.ts
 *
 * Unit tests for filter utility functions.
 * Tests edge cases, type safety, and immutability.
 */

import { describe, it, expect } from 'vitest'
import { calculateActiveFilterCount } from '../filterUtils'

describe('calculateActiveFilterCount', () => {
  describe('edge cases', () => {
    it('returns 0 when no arguments provided', () => {
      const result = calculateActiveFilterCount()
      expect(result).toBe(0)
    })

    it('returns 0 when all sets are empty', () => {
      const result = calculateActiveFilterCount(new Set(), new Set(), new Set())
      expect(result).toBe(0)
    })

    it('handles single set with items', () => {
      const set = new Set([1, 2, 3])
      const result = calculateActiveFilterCount(set)
      expect(result).toBe(3)
    })
  })

  describe('multiple sets', () => {
    it('sums two sets correctly', () => {
      const setA = new Set([1, 2])
      const setB = new Set(['a', 'b', 'c'])
      const result = calculateActiveFilterCount(setA, setB)
      expect(result).toBe(5)
    })

    it('handles mixed types (string, number, Season)', () => {
      type Season = number
      const strings = new Set(['ZAYIN', 'TETH'])
      const numbers = new Set([10, 20, 30])
      const seasons = new Set<Season>([1, 2])
      const result = calculateActiveFilterCount(strings, numbers, seasons)
      expect(result).toBe(7)
    })
  })

  describe('immutability', () => {
    it('does not mutate input sets', () => {
      const setA = new Set([1, 2, 3])
      const setB = new Set(['a', 'b'])
      const originalSizeA = setA.size
      const originalSizeB = setB.size

      calculateActiveFilterCount(setA, setB)

      expect(setA.size).toBe(originalSizeA)
      expect(setB.size).toBe(originalSizeB)
      expect(Array.from(setA)).toEqual([1, 2, 3])
      expect(Array.from(setB)).toEqual(['a', 'b'])
    })
  })

  describe('real-world scenarios', () => {
    it('calculates Identity page filter count (7 filter types)', () => {
      const selectedSinners = new Set(['1', '2', '3'])
      const selectedSeasons = new Set([1, 2])
      const selectedAffinities = new Set(['WRATH', 'LUST'])
      const selectedSinAffinities = new Set(['SLOTH'])
      const selectedDamageTypes = new Set(['SLASH'])
      const selectedDefenseTypes = new Set(['PIERCE', 'BLUNT'])
      const selectedUnitKeywords = new Set(['keyword1'])

      const result = calculateActiveFilterCount(
        selectedSinners,
        selectedSeasons,
        selectedAffinities,
        selectedSinAffinities,
        selectedDamageTypes,
        selectedDefenseTypes,
        selectedUnitKeywords
      )

      expect(result).toBe(12)
    })

    it('calculates EGO page filter count (6 filter types)', () => {
      const selectedSinners = new Set(['1', '2'])
      const selectedSeasons = new Set([1, 2, 3])
      const selectedEGOTypes = new Set(['ZAYIN', 'TETH'])
      const selectedAffinities = new Set(['WRATH'])
      const selectedSinAffinities = new Set(['LUST', 'GLOOM'])
      const selectedUnitKeywords = new Set(['keyword1', 'keyword2'])

      const result = calculateActiveFilterCount(
        selectedSinners,
        selectedSeasons,
        selectedEGOTypes,
        selectedAffinities,
        selectedSinAffinities,
        selectedUnitKeywords
      )

      expect(result).toBe(12)
    })

    it('returns correct count regardless of argument order', () => {
      const setA = new Set([1, 2])
      const setB = new Set(['a', 'b', 'c'])

      const resultAB = calculateActiveFilterCount(setA, setB)
      const resultBA = calculateActiveFilterCount(setB, setA)

      expect(resultAB).toBe(5)
      expect(resultBA).toBe(5)
      expect(resultAB).toBe(resultBA)
    })
  })

  describe('type safety', () => {
    it('accepts Set<EGOType> with literal union types', () => {
      type EGOType = 'ZAYIN' | 'TETH' | 'HE' | 'WAW' | 'ALEPH'
      const egoTypes = new Set<EGOType>(['ZAYIN', 'TETH'])
      const result = calculateActiveFilterCount(egoTypes)
      expect(result).toBe(2)
    })

    it('accepts Set<Season> with number types', () => {
      type Season = number
      const seasons = new Set<Season>([1, 2, 3, 7])
      const result = calculateActiveFilterCount(seasons)
      expect(result).toBe(4)
    })
  })
})

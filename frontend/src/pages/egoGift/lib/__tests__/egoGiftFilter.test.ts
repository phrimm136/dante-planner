/**
 * egoGiftFilter.test.ts
 *
 * Unit tests for EGO Gift value derivations.
 * Tests tier extraction and difficulty derivation.
 */

import { describe, it, expect } from 'vitest'
import { extractTier, deriveDifficulty } from '../egoGiftFilter'

describe('extractTier', () => {
  describe('valid tier extraction', () => {
    it('extracts Tier I from TIER_1 tag', () => {
      expect(extractTier(['TIER_1', 'GIFT'])).toBe('I')
    })

    it('extracts Tier II from TIER_2 tag', () => {
      expect(extractTier(['TIER_2'])).toBe('II')
    })

    it('extracts Tier III from TIER_3 tag', () => {
      expect(extractTier(['OTHER', 'TIER_3', 'GIFT'])).toBe('III')
    })

    it('extracts Tier IV from TIER_4 tag', () => {
      expect(extractTier(['TIER_4'])).toBe('IV')
    })

    it('extracts Tier V from TIER_5 tag', () => {
      expect(extractTier(['TIER_5', 'SPECIAL'])).toBe('V')
    })

    it('extracts Tier EX from TIER_EX tag', () => {
      expect(extractTier(['TIER_EX'])).toBe('EX')
    })
  })

  describe('edge cases', () => {
    it('returns undefined for empty tag array', () => {
      expect(extractTier([])).toBeUndefined()
    })

    it('returns undefined when no tier tag present', () => {
      expect(extractTier(['GIFT', 'SPECIAL', 'OTHER'])).toBeUndefined()
    })

    it('returns first tier when multiple tier tags present', () => {
      // Should return TIER_2 (II) as it comes first
      expect(extractTier(['TIER_2', 'TIER_5'])).toBe('II')
    })

    it('ignores similar but invalid tier tags', () => {
      expect(extractTier(['TIER_7', 'TIER_0', 'TIER_'])).toBeUndefined()
    })
  })
})

describe('deriveDifficulty', () => {
  describe('difficulty precedence', () => {
    it('returns normal when no difficulty flags set', () => {
      expect(deriveDifficulty({})).toBe('normal')
    })

    it('returns hard when hardOnly is true', () => {
      expect(deriveDifficulty({ hardOnly: true })).toBe('hard')
    })

    it('returns extreme when extremeOnly is true', () => {
      expect(deriveDifficulty({ extremeOnly: true })).toBe('extreme')
    })

    it('returns extreme when both flags are true (extremeOnly takes precedence)', () => {
      expect(deriveDifficulty({ hardOnly: true, extremeOnly: true })).toBe('extreme')
    })
  })

  describe('falsy values', () => {
    it('returns normal when hardOnly is false', () => {
      expect(deriveDifficulty({ hardOnly: false })).toBe('normal')
    })

    it('returns normal when extremeOnly is false', () => {
      expect(deriveDifficulty({ extremeOnly: false })).toBe('normal')
    })

    it('returns hard when hardOnly true but extremeOnly false', () => {
      expect(deriveDifficulty({ hardOnly: true, extremeOnly: false })).toBe('hard')
    })

    it('returns normal when both are undefined', () => {
      expect(deriveDifficulty({ hardOnly: undefined, extremeOnly: undefined })).toBe('normal')
    })
  })
})

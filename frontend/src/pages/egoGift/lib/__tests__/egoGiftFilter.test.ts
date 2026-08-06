/**
 * egoGiftFilter.test.ts
 *
 * Unit tests for EGO Gift value derivations.
 */

import { describe, it, expect } from 'vitest'
import { deriveDifficulty } from '../egoGiftFilter'

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

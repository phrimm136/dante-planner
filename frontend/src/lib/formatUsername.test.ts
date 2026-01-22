/**
 * formatUsername.test.ts
 *
 * Tests for username formatting utility.
 * Uses Vitest for testing framework.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { formatUsername } from './formatUsername'

// Mock i18next
vi.mock('i18next', () => ({
  default: {
    t: vi.fn((key: string, options?: { ns?: string; defaultValue?: string }) => {
      // Mock epithet namespace translations
      const translations: Record<string, Record<string, string>> = {
        epithet: {
          sinner: 'sinner',
          W_CORP: 'WCorp',
          BLADE_LINEAGE: 'Blade',
          LOBOTOMY_BRANCH: 'Lobotomy',
        },
        common: {
          unknown: 'Unknown',
        },
      }

      const ns = options?.ns || 'common'
      const translation = translations[ns]?.[key]

      // Return translation if found, otherwise defaultValue or key
      return translation ?? options?.defaultValue ?? key
    }),
  },
}))

describe('formatUsername', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('valid inputs', () => {
    it('formats username with translated keyword', () => {
      const result = formatUsername('W_CORP', 'AB123')

      expect(result).toBe('WCorpsinner#AB123')
    })

    it('formats username with different association', () => {
      const result = formatUsername('BLADE_LINEAGE', 'XY789')

      expect(result).toBe('Bladesinner#XY789')
    })

    it('formats username with Lobotomy association', () => {
      const result = formatUsername('LOBOTOMY_BRANCH', 'ZZ000')

      expect(result).toBe('Lobotomysinner#ZZ000')
    })
  })

  describe('missing translation', () => {
    it('uses keyword as fallback when translation missing', () => {
      const result = formatUsername('UNKNOWN_CORP', 'AB123')

      // Should use the keyword as-is when no translation exists
      expect(result).toBe('UNKNOWN_CORPsinner#AB123')
    })

    it('handles new/unknown association keywords gracefully', () => {
      const result = formatUsername('FUTURE_FACTION', 'TEST1')

      expect(result).toBe('FUTURE_FACTIONsinner#TEST1')
    })
  })

  describe('empty/missing inputs', () => {
    it('returns Unknown for empty keyword', () => {
      const result = formatUsername('', 'AB123')

      expect(result).toBe('Unknown')
    })

    it('returns Unknown for empty suffix', () => {
      const result = formatUsername('W_CORP', '')

      expect(result).toBe('Unknown')
    })

    it('returns Unknown for both empty', () => {
      const result = formatUsername('', '')

      expect(result).toBe('Unknown')
    })
  })

  describe('edge cases', () => {
    it('handles special characters in suffix', () => {
      const result = formatUsername('W_CORP', 'A1B2C')

      expect(result).toBe('WCorpsinner#A1B2C')
    })

    it('handles numeric suffix', () => {
      const result = formatUsername('W_CORP', '12345')

      expect(result).toBe('WCorpsinner#12345')
    })

    it('handles lowercase keyword (uses as-is if no translation)', () => {
      const result = formatUsername('w_corp', 'AB123')

      // Lowercase won't match the translation key, so uses as-is
      expect(result).toBe('w_corpsinner#AB123')
    })
  })
})

/**
 * egoGiftFilter.test.ts
 *
 * Unit tests for EGO Gift filter utility functions.
 * Tests tier extraction, difficulty derivation, and filter matching logic.
 */

import { describe, it, expect } from 'vitest'
import {
  extractTier,
  deriveDifficulty,
  matchesKeywordFilter,
  matchesDifficultyFilter,
  matchesTierFilter,
  matchesThemePackFilter,
  matchesAttributeTypeFilter,
} from '../egoGiftFilter'

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

describe('matchesKeywordFilter', () => {
  it('returns true when no keywords selected (empty filter)', () => {
    expect(matchesKeywordFilter('Burn', new Set())).toBe(true)
  })

  it('returns true when gift keyword is in selected keywords', () => {
    expect(matchesKeywordFilter('Burn', new Set(['Burn', 'Bleed']))).toBe(true)
  })

  it('returns false when gift keyword is not in selected keywords', () => {
    expect(matchesKeywordFilter('Burn', new Set(['Bleed', 'Sinking']))).toBe(false)
  })

  it('returns false when gift has no keyword but filter is active', () => {
    expect(matchesKeywordFilter(undefined, new Set(['Burn']))).toBe(false)
  })

  it('returns true when gift has no keyword and filter is empty', () => {
    expect(matchesKeywordFilter(undefined, new Set())).toBe(true)
  })
})

describe('matchesDifficultyFilter', () => {
  it('returns true when no difficulties selected (empty filter)', () => {
    expect(matchesDifficultyFilter({}, new Set())).toBe(true)
  })

  it('returns true for normal gift when normal is selected', () => {
    expect(matchesDifficultyFilter({}, new Set(['normal']))).toBe(true)
  })

  it('returns true for hard gift when hard is selected', () => {
    expect(matchesDifficultyFilter({ hardOnly: true }, new Set(['hard']))).toBe(true)
  })

  it('returns true for extreme gift when extreme is selected', () => {
    expect(matchesDifficultyFilter({ extremeOnly: true }, new Set(['extreme']))).toBe(true)
  })

  it('returns false for normal gift when only hard selected', () => {
    expect(matchesDifficultyFilter({}, new Set(['hard']))).toBe(false)
  })

  it('returns true for hard gift when both hard and extreme selected (OR logic)', () => {
    expect(matchesDifficultyFilter({ hardOnly: true }, new Set(['hard', 'extreme']))).toBe(true)
  })
})

describe('matchesTierFilter', () => {
  it('returns true when no tiers selected (empty filter)', () => {
    expect(matchesTierFilter(['TIER_3'], new Set())).toBe(true)
  })

  it('returns true when gift tier matches selected tier', () => {
    expect(matchesTierFilter(['TIER_3'], new Set(['III']))).toBe(true)
  })

  it('returns false when gift tier does not match selected tier', () => {
    expect(matchesTierFilter(['TIER_3'], new Set(['I', 'II']))).toBe(false)
  })

  it('returns false when gift has no tier tag but filter is active', () => {
    expect(matchesTierFilter(['GIFT'], new Set(['III']))).toBe(false)
  })

  it('returns true when gift tier is one of multiple selected tiers (OR logic)', () => {
    expect(matchesTierFilter(['TIER_5'], new Set(['IV', 'V', 'EX']))).toBe(true)
  })
})

describe('matchesThemePackFilter', () => {
  it('returns true when no theme packs selected (empty filter)', () => {
    expect(matchesThemePackFilter(['pack1'], new Set())).toBe(true)
  })

  it('returns true when gift theme pack matches selected pack (string)', () => {
    expect(matchesThemePackFilter(['pack1', 'pack2'], new Set(['pack1']))).toBe(true)
  })

  it('returns true when gift theme pack matches selected pack (number converted)', () => {
    expect(matchesThemePackFilter([1, 2], new Set(['1']))).toBe(true)
  })

  it('returns false when gift theme pack does not match any selected', () => {
    expect(matchesThemePackFilter(['pack1'], new Set(['pack2', 'pack3']))).toBe(false)
  })

  it('returns false when gift has no theme packs but filter is active', () => {
    expect(matchesThemePackFilter([], new Set(['pack1']))).toBe(false)
  })

  it('returns true when gift has undefined theme packs and filter is empty', () => {
    expect(matchesThemePackFilter(undefined, new Set())).toBe(true)
  })

  it('returns false when gift has undefined theme packs but filter is active', () => {
    expect(matchesThemePackFilter(undefined, new Set(['pack1']))).toBe(false)
  })

  it('returns true when any gift theme pack matches any selected (OR logic)', () => {
    expect(matchesThemePackFilter(['pack1', 'pack2'], new Set(['pack2', 'pack3']))).toBe(true)
  })
})

describe('matchesAttributeTypeFilter', () => {
  it('returns true when no attribute types selected (empty filter)', () => {
    expect(matchesAttributeTypeFilter('CRIMSON', new Set())).toBe(true)
  })

  it('returns true when gift attribute type matches selected type', () => {
    expect(matchesAttributeTypeFilter('CRIMSON', new Set(['CRIMSON']))).toBe(true)
  })

  it('returns false when gift attribute type does not match selected type', () => {
    expect(matchesAttributeTypeFilter('CRIMSON', new Set(['AMBER', 'SCARLET']))).toBe(false)
  })

  it('returns false when gift has no attribute type but filter is active', () => {
    expect(matchesAttributeTypeFilter(undefined, new Set(['CRIMSON']))).toBe(false)
  })

  it('returns true when gift has no attribute type and filter is empty', () => {
    expect(matchesAttributeTypeFilter(undefined, new Set())).toBe(true)
  })

  it('returns true when gift type is one of multiple selected (OR logic)', () => {
    expect(matchesAttributeTypeFilter('AMBER', new Set(['CRIMSON', 'AMBER']))).toBe(true)
  })
})

describe('cross-filter AND logic', () => {
  // Simulates how EGOGiftList combines filters
  function matchesAllFilters(
    gift: {
      keyword?: string
      hardOnly?: boolean
      extremeOnly?: boolean
      tag: readonly string[]
      themePack?: readonly string[]
      attributeType?: string
    },
    filters: {
      keywords: Set<string>
      difficulties: Set<'normal' | 'hard' | 'extreme'>
      tiers: Set<string>
      themePacks: Set<string>
      attributeTypes: Set<string>
    }
  ): boolean {
    return (
      matchesKeywordFilter(gift.keyword, filters.keywords) &&
      matchesDifficultyFilter(gift, filters.difficulties) &&
      matchesTierFilter(gift.tag, filters.tiers) &&
      matchesThemePackFilter(gift.themePack, filters.themePacks) &&
      matchesAttributeTypeFilter(gift.attributeType, filters.attributeTypes)
    )
  }

  const sampleGift = {
    keyword: 'Burn',
    hardOnly: true,
    tag: ['TIER_3', 'GIFT'] as const,
    themePack: ['pack1'] as const,
    attributeType: 'CRIMSON',
  }

  const emptyFilters = {
    keywords: new Set<string>(),
    difficulties: new Set<'normal' | 'hard' | 'extreme'>(),
    tiers: new Set<string>(),
    themePacks: new Set<string>(),
    attributeTypes: new Set<string>(),
  }

  it('matches when all filters are empty', () => {
    expect(matchesAllFilters(sampleGift, emptyFilters)).toBe(true)
  })

  it('matches when all active filters pass', () => {
    const filters = {
      keywords: new Set(['Burn']),
      difficulties: new Set<'normal' | 'hard' | 'extreme'>(['hard']),
      tiers: new Set(['III']),
      themePacks: new Set(['pack1']),
      attributeTypes: new Set(['CRIMSON']),
    }
    expect(matchesAllFilters(sampleGift, filters)).toBe(true)
  })

  it('fails when one filter does not match (AND logic)', () => {
    const filters = {
      keywords: new Set(['Bleed']), // Does not match
      difficulties: new Set<'normal' | 'hard' | 'extreme'>(['hard']),
      tiers: new Set(['III']),
      themePacks: new Set(['pack1']),
      attributeTypes: new Set(['CRIMSON']),
    }
    expect(matchesAllFilters(sampleGift, filters)).toBe(false)
  })

  it('fails when difficulty filter does not match', () => {
    const filters = {
      ...emptyFilters,
      difficulties: new Set<'normal' | 'hard' | 'extreme'>(['normal', 'extreme']), // Gift is hard
    }
    expect(matchesAllFilters(sampleGift, filters)).toBe(false)
  })

  it('fails when tier filter does not match', () => {
    const filters = {
      ...emptyFilters,
      tiers: new Set(['I', 'II']), // Gift is Tier III
    }
    expect(matchesAllFilters(sampleGift, filters)).toBe(false)
  })
})

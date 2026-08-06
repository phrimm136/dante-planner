/**
 * egoGiftTier.test.ts
 *
 * Tier parsing and its Roman-numeral view, over the tag shapes the sort ladder,
 * the tier facet and the card indicator all read.
 */

import { describe, it, expect } from 'vitest'

import { parseTier, toRomanTier } from '../egoGiftTier'
import type { EGOGiftTierValue } from '../egoGiftTier'

describe('parseTier', () => {
  const cases: { name: string; tags: string[]; tier: EGOGiftTierValue | null }[] = [
    { name: 'reads the tier digit out of the tag array', tags: ['GIFT', 'TIER_3'], tier: '3' },
    { name: 'reads a leading tier tag', tags: ['TIER_1', 'GIFT'], tier: '1' },
    { name: 'reads a lone tier tag', tags: ['TIER_2'], tier: '2' },
    { name: 'reads a tier tag between others', tags: ['OTHER', 'TIER_3', 'GIFT'], tier: '3' },
    { name: 'reads a trailing tier tag', tags: ['TIER_4'], tier: '4' },
    { name: 'reads a tier tag before a plain tag', tags: ['TIER_5', 'SPECIAL'], tier: '5' },
    { name: 'reads TIER_EX', tags: ['TIER_EX'], tier: 'EX' },
    { name: 'prefers TIER_EX over a later numeric tier', tags: ['TIER_EX', 'TIER_3'], tier: 'EX' },
    {
      name: 'prefers TIER_EX over an earlier numeric tier',
      tags: ['TIER_3', 'TIER_EX'],
      tier: 'EX',
    },
    { name: 'takes the first of several numeric tiers', tags: ['TIER_2', 'TIER_5'], tier: '2' },
    { name: 'returns null for an empty tag array', tags: [], tier: null },
    { name: 'returns null when no tier tag is present', tags: ['GIFT'], tier: null },
    { name: 'returns null for plain tags only', tags: ['GIFT', 'SPECIAL', 'OTHER'], tier: null },
    {
      name: 'ignores similar but invalid tier tags',
      tags: ['TIER_7', 'TIER_0', 'TIER_'],
      tier: null,
    },
  ]

  it.each(cases)('$name', ({ tags, tier }) => {
    expect(parseTier(tags)).toBe(tier)
  })
})

describe('toRomanTier', () => {
  const cases: { tier: EGOGiftTierValue; roman: string }[] = [
    { tier: '1', roman: 'I' },
    { tier: '2', roman: 'II' },
    { tier: '3', roman: 'III' },
    { tier: '4', roman: 'IV' },
    { tier: '5', roman: 'V' },
    { tier: 'EX', roman: 'EX' },
  ]

  it.each(cases)('renders tier $tier as $roman', ({ tier, roman }) => {
    expect(toRomanTier(tier)).toBe(roman)
  })

  it('passes an absent tier through', () => {
    expect(toRomanTier(null)).toBeUndefined()
  })

  it('reads a display tier straight off a tag array', () => {
    expect(toRomanTier(parseTier(['TIER_3', 'GIFT']))).toBe('III')
    expect(toRomanTier(parseTier(['GIFT']))).toBeUndefined()
  })
})

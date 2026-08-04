import { describe, it, expect } from 'vitest'

import { extractTier } from '../egoGiftSort'

describe('extractTier', () => {
  it('reads the tier digit out of the tag array', () => {
    expect(extractTier(['GIFT', 'TIER_3'])).toBe('3')
  })

  it('prefers TIER_EX over a numeric tier regardless of tag order', () => {
    expect(extractTier(['TIER_3', 'TIER_EX'])).toBe('EX')
    expect(extractTier(['TIER_EX', 'TIER_3'])).toBe('EX')
  })

  it('returns null when no tier tag is present', () => {
    expect(extractTier(['GIFT'])).toBeNull()
  })
})

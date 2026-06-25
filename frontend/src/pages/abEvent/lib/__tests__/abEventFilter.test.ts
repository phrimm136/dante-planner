import { describe, it, expect } from 'vitest'
import {
  matchesRelatedEgoGiftFilter,
  matchesRelatedThemePackFilter,
} from '../abEventFilter'
import type { AbEventSpecListEntry } from '../../schemas/AbEventSchemas'

function makeEntry(overrides: Partial<AbEventSpecListEntry> = {}): AbEventSpecListEntry {
  return {
    relatedEgoGifts: ['9001', '991002'],
    relatedThemePacks: ['1002', '1003'],
    hasImage: true,
    ...overrides,
  }
}

describe('matchesRelatedEgoGiftFilter', () => {
  it('returns true when no filter selected', () => {
    expect(matchesRelatedEgoGiftFilter(makeEntry(), new Set())).toBe(true)
  })

  it('matches when event has the selected gift', () => {
    expect(matchesRelatedEgoGiftFilter(makeEntry(), new Set(['9001']))).toBe(true)
  })

  it('does not match when event lacks the selected gift', () => {
    expect(matchesRelatedEgoGiftFilter(makeEntry(), new Set(['9999']))).toBe(false)
  })

  it('matches with OR logic — any selected gift', () => {
    expect(matchesRelatedEgoGiftFilter(makeEntry(), new Set(['9999', '991002']))).toBe(true)
  })

  it('handles empty relatedEgoGifts', () => {
    const noGifts = makeEntry({ relatedEgoGifts: [] })
    expect(matchesRelatedEgoGiftFilter(noGifts, new Set(['9001']))).toBe(false)
  })
})

describe('matchesRelatedThemePackFilter', () => {
  it('returns true when no filter selected', () => {
    expect(matchesRelatedThemePackFilter(makeEntry(), new Set())).toBe(true)
  })

  it('matches when event has the selected theme pack', () => {
    expect(matchesRelatedThemePackFilter(makeEntry(), new Set(['1002']))).toBe(true)
  })

  it('does not match when event lacks the selected pack', () => {
    expect(matchesRelatedThemePackFilter(makeEntry(), new Set(['9999']))).toBe(false)
  })

  it('handles events with no theme packs (orphans)', () => {
    const orphan = makeEntry({ relatedThemePacks: [] })
    expect(matchesRelatedThemePackFilter(orphan, new Set())).toBe(true)
    expect(matchesRelatedThemePackFilter(orphan, new Set(['1002']))).toBe(false)
  })
})

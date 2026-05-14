/**
 * egoPassiveSelection.test.ts
 *
 * Unit tests for the EGO passive selection helpers.
 *
 * The two motivating shapes are:
 *
 * 1. Single-passive EGO (the common case, 99/100 in raw data):
 *    `passiveList = [[], ['2010111'], [], []]` — passive active from t2 onward.
 *
 * 2. Tier-replacement EGO (today's only example, EGO 20402):
 *    `passiveList = [[], ['2040211'], [], [], ['2040212']]`
 *    `2040211` is active at threadspin 2-4; `2040212` *replaces* it at
 *    threadspin 5. They share a slot key (`204021`) so the t5 variant must
 *    NOT appear as a dimmed "future" passive at threadspin 2-4.
 */

import { describe, it, expect } from 'vitest'
import {
  getEgoPassiveSlotKey,
  getEffectiveEgoPassives,
  getLockedEgoPassives,
} from '../egoPassiveSelection'

describe('getEgoPassiveSlotKey', () => {
  it('drops the trailing variant digit', () => {
    expect(getEgoPassiveSlotKey('2040211')).toBe('204021')
    expect(getEgoPassiveSlotKey('2040212')).toBe('204021')
  })

  it('produces matching keys for tier-replacement siblings', () => {
    expect(getEgoPassiveSlotKey('2040211')).toBe(getEgoPassiveSlotKey('2040212'))
  })

  it('distinguishes unrelated passive IDs', () => {
    expect(getEgoPassiveSlotKey('2010111')).not.toBe(getEgoPassiveSlotKey('2010211'))
  })
})

describe('getEffectiveEgoPassives', () => {
  describe('single-passive EGO', () => {
    const single = [[], ['2010111'], [], []]

    it('returns empty at threadspin 1 (slot[0] is empty, no earlier slot)', () => {
      expect(getEffectiveEgoPassives(single, 0)).toEqual([])
    })

    it('returns the lone passive at threadspin 2', () => {
      expect(getEffectiveEgoPassives(single, 1)).toEqual(['2010111'])
    })

    it('inherits the lone passive at threadspin 3 (empty slot walks back)', () => {
      expect(getEffectiveEgoPassives(single, 2)).toEqual(['2010111'])
    })

    it('inherits the lone passive at threadspin 4', () => {
      expect(getEffectiveEgoPassives(single, 3)).toEqual(['2010111'])
    })
  })

  describe('tier-replacement EGO (20402)', () => {
    const replacement = [[], ['2040211'], [], [], ['2040212']]

    it('returns the t2-4 variant at threadspin 2-4', () => {
      expect(getEffectiveEgoPassives(replacement, 1)).toEqual(['2040211'])
      expect(getEffectiveEgoPassives(replacement, 2)).toEqual(['2040211'])
      expect(getEffectiveEgoPassives(replacement, 3)).toEqual(['2040211'])
    })

    it('swaps to the t5 variant at threadspin 5', () => {
      expect(getEffectiveEgoPassives(replacement, 4)).toEqual(['2040212'])
    })
  })

  it('returns empty for an EGO with no passives at any tier', () => {
    expect(getEffectiveEgoPassives([[], [], [], []], 3)).toEqual([])
  })

  it('handles a missing slot gracefully', () => {
    // Sparse arrays should not throw — the loop just skips undefined slots.
    const sparse = [undefined as unknown as string[], ['2010111']]
    expect(getEffectiveEgoPassives(sparse, 1)).toEqual(['2010111'])
  })
})

describe('getLockedEgoPassives', () => {
  describe('single-passive EGO', () => {
    const single = [[], ['2010111'], [], []]

    it('surfaces the future passive as locked at threadspin 1', () => {
      expect(getLockedEgoPassives(single, 0)).toEqual(['2010111'])
    })

    it('is empty once the passive is effective', () => {
      expect(getLockedEgoPassives(single, 1)).toEqual([])
      expect(getLockedEgoPassives(single, 3)).toEqual([])
    })
  })

  describe('tier-replacement EGO (20402)', () => {
    const replacement = [[], ['2040211'], [], [], ['2040212']]

    it('at threadspin 1: previews the first variant (slot not yet covered)', () => {
      // Only one slot key is uncovered (204021), so only ONE locked passive shows.
      // The t5 replacement variant collides on the same slot — should NOT also show.
      expect(getLockedEgoPassives(replacement, 0)).toEqual(['2040211'])
    })

    it('at threadspin 2-4: the t5 replacement is HIDDEN, not dimmed', () => {
      expect(getLockedEgoPassives(replacement, 1)).toEqual([])
      expect(getLockedEgoPassives(replacement, 2)).toEqual([])
      expect(getLockedEgoPassives(replacement, 3)).toEqual([])
    })

    it('at threadspin 5: nothing left to preview', () => {
      expect(getLockedEgoPassives(replacement, 4)).toEqual([])
    })
  })

  describe('hypothetical multi-slot EGO', () => {
    // Forward-compat: if PM ever ships an EGO with a genuinely NEW passive
    // slot at a higher tier (not a replacement of an existing one), it must
    // still surface as locked. Two different slot keys → no dedupe.
    const multiSlot = [[], ['2049911'], [], [], ['2049921']]

    it('shows the genuinely-new higher-tier passive as locked', () => {
      // '2049911' is effective; '2049921' has slot '204992' which is new → locked.
      expect(getLockedEgoPassives(multiSlot, 1)).toEqual(['2049921'])
    })
  })

  it('handles an EGO with no passives gracefully', () => {
    expect(getLockedEgoPassives([[], [], [], []], 0)).toEqual([])
  })
})

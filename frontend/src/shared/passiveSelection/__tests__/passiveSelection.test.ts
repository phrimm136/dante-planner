/**
 * passiveSelection.test.ts
 *
 * Unit tests for the generic tier-based passive selection core. The identity
 * and EGO wrappers cover the two production key functions; these tests pin the
 * generic behaviour itself — inheritance across empty tiers and `keyOf`-driven
 * dedupe with an element type neither slice uses.
 */

import { describe, it, expect } from 'vitest'
import { selectEffectivePassives, selectLockedPassives } from '../passiveSelection'

interface Passive {
  slot: string
  rank: number
}

const p = (slot: string, rank: number): Passive => ({ slot, rank })

describe('selectEffectivePassives', () => {
  const tiers = [[p('a', 1)], [], [p('a', 2), p('b', 1)], []]

  it('returns the tier at the index when it is non-empty', () => {
    expect(selectEffectivePassives(tiers, 0)).toEqual([p('a', 1)])
    expect(selectEffectivePassives(tiers, 2)).toEqual([p('a', 2), p('b', 1)])
  })

  it('walks down past empty tiers', () => {
    expect(selectEffectivePassives(tiers, 1)).toEqual([p('a', 1)])
    expect(selectEffectivePassives(tiers, 3)).toEqual([p('a', 2), p('b', 1)])
  })

  it('returns the tier array itself, not a copy', () => {
    expect(selectEffectivePassives(tiers, 0)).toBe(tiers[0])
  })

  it('returns empty when every tier at or below the index is empty', () => {
    expect(selectEffectivePassives([[], []], 1)).toEqual([])
  })

  it('skips a missing tier', () => {
    const sparse = [undefined as unknown as Passive[], [p('a', 1)]]
    expect(selectEffectivePassives(sparse, 1)).toEqual([p('a', 1)])
  })
})

describe('selectLockedPassives', () => {
  const keyOf = (passive: Passive) => passive.slot

  it('previews higher-tier passives whose key is not yet covered', () => {
    const tiers = [[p('a', 1)], [p('b', 1)], [p('c', 1)]]
    expect(selectLockedPassives(tiers, 0, keyOf)).toEqual([p('b', 1), p('c', 1)])
  })

  it('hides a higher-tier passive that replaces an effective one', () => {
    const tiers = [[p('a', 1)], [], [p('a', 2)]]
    expect(selectLockedPassives(tiers, 0, keyOf)).toEqual([])
  })

  it('keeps only the lowest tier among higher-tier passives sharing a key', () => {
    const tiers = [[], [p('a', 1)], [p('a', 2)]]
    expect(selectLockedPassives(tiers, 0, keyOf)).toEqual([p('a', 1)])
  })

  it('drops an element that is itself effective, even from a higher tier', () => {
    const shared = p('a', 1)
    const tiers = [[shared], [shared, p('b', 1)]]
    expect(selectLockedPassives(tiers, 0, keyOf)).toEqual([p('b', 1)])
  })

  it('supports numeric keys', () => {
    const tiers = [[10], [11], [20]]
    expect(selectLockedPassives(tiers, 0, (id: number) => Math.floor(id / 10))).toEqual([20])
  })

  it('returns empty at the top tier', () => {
    const tiers = [[p('a', 1)], [p('b', 1)]]
    expect(selectLockedPassives(tiers, 1, keyOf)).toEqual([])
  })

  it('skips missing tiers above the index', () => {
    const sparse = [[p('a', 1)], undefined as unknown as Passive[], [p('b', 1)]]
    expect(selectLockedPassives(sparse, 0, keyOf)).toEqual([p('b', 1)])
  })
})

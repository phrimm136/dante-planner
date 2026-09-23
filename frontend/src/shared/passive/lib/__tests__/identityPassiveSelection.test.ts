/**
 * identityPassiveSelection.test.ts
 *
 * Unit tests for the identity passive selection helpers.
 *
 * The motivating shape is identity 10114 (Heishou Pack), whose battle passive
 * list carries an enhanced passive at uptie 4:
 *
 *   [[pid('1011402'), pid('1011403')], [pid('1011402'), pid('1011403'), pid('1011401')], [], [pid('1011402'), pid('1011403'), pid('1011411')]]
 *
 * `pid('1011411')` is the enhanced form of `pid('1011401')` — same variant (1), type 1 vs 0.
 * It replaces the base rather than standing beside it, so it must never show
 * as a dimmed "future" passive once variant 1 is already on screen.
 */

import { describe, it, expect } from 'vitest'
import {
  getPassiveInfo,
  getEffectivePassives,
  getLockedPassives,
} from '../identityPassiveSelection'
import { PassiveIdSchema } from '@/shared/gameData'
import type { PassiveId } from '@/shared/gameData'

const pid = (id: string): PassiveId => PassiveIdSchema.parse(id)

const heishouBattle = [
  [pid('1011402'), pid('1011403')],
  [pid('1011402'), pid('1011403'), pid('1011401')],
  [],
  [pid('1011402'), pid('1011403'), pid('1011411')],
]

const heishouSupport = [[], [], [pid('1011421')], []]

describe('getPassiveInfo', () => {
  it('decodes type and variant from the ID suffix', () => {
    expect(getPassiveInfo(pid('1011401'))).toEqual({ type: 0, variant: 1 })
    expect(getPassiveInfo(pid('1011402'))).toEqual({ type: 0, variant: 2 })
    expect(getPassiveInfo(pid('1011411'))).toEqual({ type: 1, variant: 1 })
    expect(getPassiveInfo(pid('1011421'))).toEqual({ type: 2, variant: 1 })
  })

  it('gives an enhanced passive the same variant as its base', () => {
    expect(getPassiveInfo(pid('1011411')).variant).toBe(getPassiveInfo(pid('1011401')).variant)
  })
})

describe('getEffectivePassives', () => {
  it('returns the tier itself when it is non-empty', () => {
    expect(getEffectivePassives(heishouBattle, 0)).toEqual([pid('1011402'), pid('1011403')])
    expect(getEffectivePassives(heishouBattle, 1)).toEqual([
      pid('1011402'),
      pid('1011403'),
      pid('1011401'),
    ])
    expect(getEffectivePassives(heishouBattle, 3)).toEqual([
      pid('1011402'),
      pid('1011403'),
      pid('1011411'),
    ])
  })

  it('inherits from the closest non-empty tier below', () => {
    expect(getEffectivePassives(heishouBattle, 2)).toEqual([
      pid('1011402'),
      pid('1011403'),
      pid('1011401'),
    ])
  })

  it('returns empty when no tier at or below the index has passives', () => {
    expect(getEffectivePassives(heishouSupport, 0)).toEqual([])
    expect(getEffectivePassives(heishouSupport, 1)).toEqual([])
  })

  it('keeps inheriting past a trailing empty tier', () => {
    expect(getEffectivePassives(heishouSupport, 3)).toEqual([pid('1011421')])
  })

  it('returns empty for an identity with no passives at any tier', () => {
    expect(getEffectivePassives([[], [], [], []], 3)).toEqual([])
  })

  it('handles a missing tier gracefully', () => {
    const sparse = [undefined as unknown as PassiveId[], [pid('1011401')]]
    expect(getEffectivePassives(sparse, 1)).toEqual([pid('1011401')])
  })
})

describe('getLockedPassives', () => {
  it('previews a higher-tier passive whose variant is not yet shown', () => {
    expect(getLockedPassives(heishouBattle, 0)).toEqual([pid('1011401')])
  })

  it('hides the enhanced passive once its base variant is effective', () => {
    expect(getLockedPassives(heishouBattle, 1)).toEqual([])
    expect(getLockedPassives(heishouBattle, 2)).toEqual([])
  })

  it('is empty at the top tier', () => {
    expect(getLockedPassives(heishouBattle, 3)).toEqual([])
  })

  it('dedupes two higher-tier passives sharing a variant, keeping the lower tier', () => {
    const sharedVariant = [[], [], [pid('1011401')], [pid('1011411')]]
    expect(getLockedPassives(sharedVariant, 0)).toEqual([pid('1011401')])
  })

  it('previews a support passive that only appears at a higher tier', () => {
    expect(getLockedPassives(heishouSupport, 0)).toEqual([pid('1011421')])
  })

  it('handles an identity with no passives gracefully', () => {
    expect(getLockedPassives([[], [], [], []], 0)).toEqual([])
  })
})

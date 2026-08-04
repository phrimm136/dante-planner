/**
 * identityPassiveSelection.test.ts
 *
 * Unit tests for the identity passive selection helpers, plus the skill-slot
 * mapping that shares the page's data shape.
 *
 * The motivating shape is identity 10114 (Heishou Pack), whose battle passive
 * list carries an enhanced passive at uptie 4:
 *
 *   [[1011402, 1011403], [1011402, 1011403, 1011401], [], [1011402, 1011403, 1011411]]
 *
 * `1011411` is the enhanced form of `1011401` — same variant (1), type 1 vs 0.
 * It replaces the base rather than standing beside it, so it must never show
 * as a dimmed "future" passive once variant 1 is already on screen.
 */

import { describe, it, expect } from 'vitest'
import {
  getPassiveInfo,
  getEffectivePassives,
  getLockedPassives,
  getPassiveCondition,
} from '../identityPassiveSelection'
import { getSkillSlotNumber, getSkillAttributeType } from '../identitySkillSlots'
import type { SkillSlot } from '../identitySkillSlots'
import type { IdentityData, IdentitySkillEntry } from '../../types/IdentityTypes'

const heishouBattle = [
  [1011402, 1011403],
  [1011402, 1011403, 1011401],
  [],
  [1011402, 1011403, 1011411],
]

const heishouSupport = [[], [], [1011421], []]

describe('getPassiveInfo', () => {
  it('decodes type and variant from the ID suffix', () => {
    expect(getPassiveInfo(1011401)).toEqual({ type: 0, variant: 1 })
    expect(getPassiveInfo(1011402)).toEqual({ type: 0, variant: 2 })
    expect(getPassiveInfo(1011411)).toEqual({ type: 1, variant: 1 })
    expect(getPassiveInfo(1011421)).toEqual({ type: 2, variant: 1 })
  })

  it('gives an enhanced passive the same variant as its base', () => {
    expect(getPassiveInfo(1011411).variant).toBe(getPassiveInfo(1011401).variant)
  })
})

describe('getEffectivePassives', () => {
  it('returns the tier itself when it is non-empty', () => {
    expect(getEffectivePassives(heishouBattle, 0)).toEqual([1011402, 1011403])
    expect(getEffectivePassives(heishouBattle, 1)).toEqual([1011402, 1011403, 1011401])
    expect(getEffectivePassives(heishouBattle, 3)).toEqual([1011402, 1011403, 1011411])
  })

  it('inherits from the closest non-empty tier below', () => {
    expect(getEffectivePassives(heishouBattle, 2)).toEqual([1011402, 1011403, 1011401])
  })

  it('returns empty when no tier at or below the index has passives', () => {
    expect(getEffectivePassives(heishouSupport, 0)).toEqual([])
    expect(getEffectivePassives(heishouSupport, 1)).toEqual([])
  })

  it('keeps inheriting past a trailing empty tier', () => {
    expect(getEffectivePassives(heishouSupport, 3)).toEqual([1011421])
  })

  it('returns empty for an identity with no passives at any tier', () => {
    expect(getEffectivePassives([[], [], [], []], 3)).toEqual([])
  })

  it('handles a missing tier gracefully', () => {
    const sparse = [undefined as unknown as number[], [1011401]]
    expect(getEffectivePassives(sparse, 1)).toEqual([1011401])
  })
})

describe('getLockedPassives', () => {
  it('previews a higher-tier passive whose variant is not yet shown', () => {
    expect(getLockedPassives(heishouBattle, 0)).toEqual([1011401])
  })

  it('hides the enhanced passive once its base variant is effective', () => {
    expect(getLockedPassives(heishouBattle, 1)).toEqual([])
    expect(getLockedPassives(heishouBattle, 2)).toEqual([])
  })

  it('is empty at the top tier', () => {
    expect(getLockedPassives(heishouBattle, 3)).toEqual([])
  })

  it('dedupes two higher-tier passives sharing a variant, keeping the lower tier', () => {
    const sharedVariant = [[], [], [1011401], [1011411]]
    expect(getLockedPassives(sharedVariant, 0)).toEqual([1011401])
  })

  it('previews a support passive that only appears at a higher tier', () => {
    expect(getLockedPassives(heishouSupport, 0)).toEqual([1011421])
  })

  it('handles an identity with no passives gracefully', () => {
    expect(getLockedPassives([[], [], [], []], 0)).toEqual([])
  })
})

describe('getPassiveCondition', () => {
  const conditions: IdentityData['passives']['conditions'] = {
    '1011401': { type: 'STOCK', values: { SHAMROCK: 5 } },
    '1011421': { type: 'STOCK', values: { SHAMROCK: 4 } },
  }

  it('returns the passive own condition when it has one', () => {
    expect(getPassiveCondition(conditions, 1011401)).toEqual({
      type: 'STOCK',
      values: { SHAMROCK: 5 },
    })
  })

  it('falls back to the base condition for an enhanced passive', () => {
    expect(getPassiveCondition(conditions, 1011411)).toEqual({
      type: 'STOCK',
      values: { SHAMROCK: 5 },
    })
  })

  it('prefers the enhanced passive own condition over the base one', () => {
    const withEnhanced = {
      ...conditions,
      '1011411': { type: 'RESONANCE', values: { SHAMROCK: 3 } },
    }
    expect(getPassiveCondition(withEnhanced, 1011411)).toEqual({
      type: 'RESONANCE',
      values: { SHAMROCK: 3 },
    })
  })

  it('returns undefined for a base passive with no condition', () => {
    expect(getPassiveCondition(conditions, 1011403)).toBeUndefined()
  })

  it('returns undefined for an enhanced passive whose base has no condition either', () => {
    expect(getPassiveCondition(conditions, 1011413)).toBeUndefined()
  })
})

describe('getSkillSlotNumber', () => {
  it('maps every slot to its image path number', () => {
    expect(getSkillSlotNumber('skill1')).toBe(1)
    expect(getSkillSlotNumber('skill2')).toBe(2)
    expect(getSkillSlotNumber('skill3')).toBe(3)
    expect(getSkillSlotNumber('skillDef')).toBe(4)
  })

  it('throws on a slot outside the union', () => {
    expect(() => getSkillSlotNumber('skill4' as SkillSlot)).toThrow('Unhandled union member')
  })
})

describe('getSkillAttributeType', () => {
  const entry = (id: number, skillData: object[]): IdentitySkillEntry =>
    ({ id, skillData }) as IdentitySkillEntry

  const skills: Record<SkillSlot, IdentitySkillEntry[]> = {
    skill1: [entry(1011401, [{ attributeType: 'AMBER', atkType: 'SLASH' }])],
    skill2: [entry(1011402, [{ attributeType: 'VIOLET' }, { attributeType: 'AZURE' }])],
    skill3: [entry(1011403, [{}, {}, { attributeType: 'SHAMROCK' }, {}])],
    skillDef: [],
  }

  it('reads the attribute type from the first level', () => {
    expect(getSkillAttributeType(skills, 'skill1')).toBe('AMBER')
  })

  it('lets a later level override an earlier one', () => {
    expect(getSkillAttributeType(skills, 'skill2')).toBe('AZURE')
  })

  it('finds an attribute type that only appears at a higher uptie', () => {
    expect(getSkillAttributeType(skills, 'skill3')).toBe('SHAMROCK')
  })

  it('returns undefined for a slot with no entries', () => {
    expect(getSkillAttributeType(skills, 'skillDef')).toBeUndefined()
  })
})

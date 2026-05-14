/**
 * EGOSchemas.test.ts
 *
 * Covers the new optional `flavor` field on EGO skill + passive i18n schemas
 * (added forward-compat — raw EGO data does not yet ship flavor, but the
 * pipeline + frontend are wired to surface it the moment PM does).
 */

import { describe, it, expect } from 'vitest'
import { EGOPassiveI18nSchema, EGOSkillI18nSchema } from '../EGOSchemas'

describe('EGOSkillI18nSchema flavor field', () => {
  const baseSkill = {
    name: 'Awakening Skill',
    descs: [{ desc: 'Deal damage.' }],
  }

  it('accepts a skill without flavor (current raw EGO state)', () => {
    const result = EGOSkillI18nSchema.safeParse(baseSkill)
    expect(result.success).toBe(true)
  })

  it('accepts a skill with a flavor lore line', () => {
    const result = EGOSkillI18nSchema.safeParse({
      ...baseSkill,
      flavor: 'A blade that cuts through space itself.',
    })
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.flavor).toBe('A blade that cuts through space itself.')
    }
  })

  it('rejects non-string flavor', () => {
    const result = EGOSkillI18nSchema.safeParse({
      ...baseSkill,
      flavor: { ja: 'lore' },
    })
    expect(result.success).toBe(false)
  })
})

describe('EGOPassiveI18nSchema flavor field', () => {
  const basePassive = {
    name: 'EGO Passive',
    desc: 'On Awaken Lv 2+, gain X.',
  }

  it('accepts a passive without flavor (current raw EGO state)', () => {
    const result = EGOPassiveI18nSchema.safeParse(basePassive)
    expect(result.success).toBe(true)
  })

  it('accepts a passive with a flavor lore line', () => {
    const result = EGOPassiveI18nSchema.safeParse({
      ...basePassive,
      flavor: 'A whispered prescript.',
    })
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.flavor).toBe('A whispered prescript.')
    }
  })

  it('rejects non-string flavor', () => {
    const result = EGOPassiveI18nSchema.safeParse({
      ...basePassive,
      flavor: false,
    })
    expect(result.success).toBe(false)
  })
})

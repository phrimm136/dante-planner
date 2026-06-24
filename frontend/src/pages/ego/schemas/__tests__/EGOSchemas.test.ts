/**
 * EGOSchemas.test.ts
 *
 * Covers the new optional `flavor` field on EGO skill + passive i18n schemas
 * (added forward-compat — raw EGO data does not yet ship flavor, but the
 * pipeline + frontend are wired to surface it the moment PM does).
 */

import { describe, it, expect } from 'vitest'
import {
  EGODataSchema,
  EGOPassiveI18nSchema,
  EGOSkillI18nSchema,
} from '../EGOSchemas'

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

describe('EGODataSchema', () => {
  // EGOData derives from this schema via z.infer (single source of truth).
  // `.strict()` is applied only here to assert no-unknown-key behavior; the
  // production schema stays non-strict so a runtime sample carrying extra
  // producer fields is not rejected at load.
  const validEGOData = {
    updatedDate: 20241128,
    egoType: 'HE',
    season: 0,
    attributeResist: {
      AMBER: 0.5,
      AZURE: 2,
      VIOLET: 0.75,
    },
    requirements: {
      AMBER: 2,
      AZURE: 1,
    },
    skills: {
      awaken: [
        {
          id: 2070811,
          skillData: [
            {
              attributeType: 'AMBER',
              atkType: 'HIT',
              targetNum: 3,
              mpUsage: 20,
              skillLevelCorrection: 0,
              defaultValue: 8,
              scale: 8,
              coinString: 'CC',
            },
            {},
            { scale: 8 },
            { scale: 8 },
          ],
        },
      ],
      erosion: [
        {
          id: 2070821,
          skillData: [
            { attributeType: 'AMBER', atkType: 'HIT', defaultValue: 28 },
            {},
            {},
            {},
          ],
        },
      ],
    },
    passives: {
      passiveList: [[], [], [], []],
    },
    maxThreadspin: 4,
  }

  it('accepts a valid EGO detail sample', () => {
    expect(EGODataSchema.safeParse(validEGOData).success).toBe(true)
  })

  it('rejects an unknown key under strict()', () => {
    const withExtra = { ...validEGOData, bogusField: 1 }
    expect(EGODataSchema.strict().safeParse(withExtra).success).toBe(false)
  })
})

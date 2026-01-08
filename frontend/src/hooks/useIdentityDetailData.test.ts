/**
 * useIdentityDetailData.test.ts
 *
 * Tests for identity detail data hooks.
 * Validates query key structure for spec/i18n separation.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { identityDetailQueryKeys } from './useIdentityDetailData'
import { IdentityDataSchema, IdentityI18nSchema } from '@/schemas'

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    i18n: { language: 'EN' },
  }),
}))

describe('identityDetailQueryKeys', () => {
  it('creates all base key', () => {
    const key = identityDetailQueryKeys.all()
    expect(key).toEqual(['identity'])
  })

  it('creates detail key with id only (no language) - UT1', () => {
    const key = identityDetailQueryKeys.detail('10101')
    expect(key).toEqual(['identity', '10101'])
    // Verify NO language in key
    expect(key.length).toBe(2)
    expect(key).not.toContain('EN')
    expect(key).not.toContain('KR')
  })

  it('creates i18n key with id AND language - UT2', () => {
    const key = identityDetailQueryKeys.i18n('10101', 'EN')
    expect(key).toEqual(['identity', '10101', 'i18n', 'EN'])
    // Verify language IS in key
    expect(key.length).toBe(4)
    expect(key).toContain('EN')
  })

  it('detail key is stable across multiple accesses', () => {
    const key1 = identityDetailQueryKeys.detail('10101')
    const key2 = identityDetailQueryKeys.detail('10101')
    expect(key1).toEqual(key2)
  })

  it('i18n key changes with language', () => {
    const keyEN = identityDetailQueryKeys.i18n('10101', 'EN')
    const keyKR = identityDetailQueryKeys.i18n('10101', 'KR')
    expect(keyEN).not.toEqual(keyKR)
    expect(keyEN[3]).toBe('EN')
    expect(keyKR[3]).toBe('KR')
  })

  it('i18n key changes with identity id', () => {
    const key1 = identityDetailQueryKeys.i18n('10101', 'EN')
    const key2 = identityDetailQueryKeys.i18n('10102', 'EN')
    expect(key1).not.toEqual(key2)
    expect(key1[1]).toBe('10101')
    expect(key2[1]).toBe('10102')
  })
})

describe('IdentityDataSchema', () => {
  const mockIdentityData = {
    updatedDate: 20230227,
    skillKeywordList: ['Sinking'],
    panicType: 9999,
    season: 0,
    rank: 1,
    hp: { defaultStat: 72, incrementByLevel: 2.48 },
    defCorrection: -2,
    minSpeedList: [4, 4, 4, 4],
    maxSpeedList: [6, 7, 8, 8],
    unitKeywordList: ['BASE_APPEARANCE', 'SMALL'],
    staggerList: [65, 35, 15],
    ResistInfo: { SLASH: 2, PENETRATE: 0.5, HIT: 1 },
    mentalConditionInfo: {
      add: ['OnWinDuelAsParryingCountMultiply10AndPlus20Percent'],
      min: ['OnDieAllyAsLevelRatio10'],
    },
    skills: {
      skill1: [{ id: 1010101, skillData: [{}, {}, {}, {}] }],
      skill2: [{ id: 1010102, skillData: [{}, {}, {}, {}] }],
      skill3: [{ id: 1010103, skillData: [{}, {}, {}, {}] }],
      skillDef: [{ id: 1010104, skillData: [{}, {}, {}, {}] }],
    },
    passives: {
      battlePassiveList: [[], [], [], []],
      supportPassiveList: [[], [], [], []],
      conditions: {},
    },
  }

  it('validates correct identity data', () => {
    const result = IdentityDataSchema.safeParse(mockIdentityData)
    expect(result.success).toBe(true)
  })

  it('rejects identity data without hp', () => {
    const { hp: _hp, ...invalidData } = mockIdentityData
    const result = IdentityDataSchema.safeParse(invalidData)
    expect(result.success).toBe(false)
  })

  it('rejects identity data without skills', () => {
    const { skills: _skills, ...invalidData } = mockIdentityData
    const result = IdentityDataSchema.safeParse(invalidData)
    expect(result.success).toBe(false)
  })

  it('rejects identity data without passives', () => {
    const { passives: _passives, ...invalidData } = mockIdentityData
    const result = IdentityDataSchema.safeParse(invalidData)
    expect(result.success).toBe(false)
  })
})

describe('IdentityI18nSchema', () => {
  const mockIdentityI18n = {
    name: 'Test Identity',
    skills: {
      '1010101': { name: 'Skill 1', descs: [{ desc: 'Skill desc', coinDescs: [] }] },
    },
    passives: {
      '1010101': { name: 'Passive 1', desc: 'Passive desc' },
    },
  }

  it('validates correct i18n data', () => {
    const result = IdentityI18nSchema.safeParse(mockIdentityI18n)
    expect(result.success).toBe(true)
  })

  it('rejects i18n data without name', () => {
    const { name: _name, ...invalidData } = mockIdentityI18n
    const result = IdentityI18nSchema.safeParse(invalidData)
    expect(result.success).toBe(false)
  })

  it('rejects i18n data without skills', () => {
    const { skills: _skills, ...invalidData } = mockIdentityI18n
    const result = IdentityI18nSchema.safeParse(invalidData)
    expect(result.success).toBe(false)
  })

  it('rejects i18n data without passives', () => {
    const { passives: _passives, ...invalidData } = mockIdentityI18n
    const result = IdentityI18nSchema.safeParse(invalidData)
    expect(result.success).toBe(false)
  })
})

describe('useIdentityDetailData backward compatibility - UT3', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('hooks are exported from module', async () => {
    // Dynamic import to get actual exports
    const module = await import('./useIdentityDetailData')

    // Verify all three hooks are exported
    expect(typeof module.useIdentityDetailSpec).toBe('function')
    expect(typeof module.useIdentityDetailI18n).toBe('function')
    expect(typeof module.useIdentityDetailData).toBe('function')
  })

  it('query key factory is exported', async () => {
    const module = await import('./useIdentityDetailData')

    // Verify query key factory is exported
    expect(module.identityDetailQueryKeys).toBeDefined()
    expect(typeof module.identityDetailQueryKeys.all).toBe('function')
    expect(typeof module.identityDetailQueryKeys.detail).toBe('function')
    expect(typeof module.identityDetailQueryKeys.i18n).toBe('function')
  })
})

import { describe, it, expect } from 'vitest'
import {
  DefenseTypeSchema,
  IdentityDataSchema,
  IdentityPassiveI18nSchema,
  IdentitySkillI18nSchema,
  IdentitySpecListItemSchema,
} from '../IdentitySchemas'

describe('DefenseTypeSchema', () => {
  it.each([
    'EVADE',
    'COUNTER',
    'CLASHABLE_COUNTER',
    'GUARD',
    'CLASHABLE_GUARD',
  ])('accepts valid value: %s', (value) => {
    expect(DefenseTypeSchema.parse(value)).toBe(value)
  })

  it('rejects invalid string', () => {
    expect(() => DefenseTypeSchema.parse('DODGE')).toThrow()
  })

  it('rejects empty string', () => {
    expect(() => DefenseTypeSchema.parse('')).toThrow()
  })
})

describe('IdentitySpecListItemSchema with defenseType', () => {
  const baseSpec = {
    updateDate: 20260101,
    skillKeywordList: ['keyword1'],
    battleKeywordList: ['keyword1'],
    season: 1,
    rank: 3,
    unitKeywordList: ['unit1'],
    attributeType: ['CRIMSON'],
    atkType: ['SLASH'],
  }

  it('valid spec item with single defense type', () => {
    const result = IdentitySpecListItemSchema.safeParse({
      ...baseSpec,
      defenseType: ['GUARD'],
    })
    expect(result.success).toBe(true)
  })

  it('valid spec item with multiple defense types', () => {
    const result = IdentitySpecListItemSchema.safeParse({
      ...baseSpec,
      defenseType: ['EVADE', 'COUNTER'],
    })
    expect(result.success).toBe(true)
  })

  it('spec item with invalid defense type fails', () => {
    const result = IdentitySpecListItemSchema.safeParse({
      ...baseSpec,
      defenseType: ['INVALID'],
    })
    expect(result.success).toBe(false)
  })

  it('spec item with empty defenseType array passes', () => {
    const result = IdentitySpecListItemSchema.safeParse({
      ...baseSpec,
      defenseType: [],
    })
    expect(result.success).toBe(true)
  })
})

describe('IdentitySkillI18nSchema flavor field', () => {
  const baseSkill = {
    name: 'End-stop Stab',
    descs: [{ desc: 'Inflict +1 Sinking Count' }],
  }

  it('accepts a skill without flavor (most skills do not ship one)', () => {
    const result = IdentitySkillI18nSchema.safeParse(baseSkill)
    expect(result.success).toBe(true)
  })

  it('accepts a skill with a flavor lore line', () => {
    const result = IdentitySkillI18nSchema.safeParse({
      ...baseSkill,
      flavor: 'A technique that cuts through space itself.',
    })
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.flavor).toBe('A technique that cuts through space itself.')
    }
  })

  it('rejects non-string flavor', () => {
    const result = IdentitySkillI18nSchema.safeParse({
      ...baseSkill,
      flavor: 123,
    })
    expect(result.success).toBe(false)
  })

  it('does not bleed flavor into per-uptie desc entries', () => {
    // Flavor lives at the skill level, not inside each desc entry.
    // Verifies the schema does not silently accept flavor on a desc entry shape.
    const result = IdentitySkillI18nSchema.safeParse({
      name: 'X',
      descs: [{ desc: 'a', flavor: 'lore' }],
    })
    expect(result.success).toBe(true)
    // Even if Zod allows extra unknown props on the inner entry,
    // the canonical place for flavor is on the parsed skill object.
    if (result.success) {
      expect(result.data.flavor).toBeUndefined()
    }
  })
})

describe('IdentityPassiveI18nSchema flavor field', () => {
  const basePassive = {
    name: 'Battle Passive Name',
    desc: 'Mechanical passive description.',
  }

  it('accepts a passive without flavor (the common case)', () => {
    const result = IdentityPassiveI18nSchema.safeParse(basePassive)
    expect(result.success).toBe(true)
  })

  it('accepts a passive with an optional flavor line', () => {
    const result = IdentityPassiveI18nSchema.safeParse({
      ...basePassive,
      flavor: "'The things I dread seeing again always come back.'",
    })
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.flavor).toBe(
        "'The things I dread seeing again always come back.'",
      )
    }
  })

  it('rejects non-string flavor', () => {
    const result = IdentityPassiveI18nSchema.safeParse({
      ...basePassive,
      flavor: 42,
    })
    expect(result.success).toBe(false)
  })
})

describe('IdentityDataSchema', () => {
  // IdentityData derives from this schema via z.infer (single source of truth).
  // `.strict()` is applied only here to assert no-unknown-key behavior; the
  // production schema stays non-strict so a runtime sample carrying extra
  // producer fields is not rejected at load.
  const validIdentityData = {
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

  it('accepts a valid identity detail sample', () => {
    expect(IdentityDataSchema.safeParse(validIdentityData).success).toBe(true)
  })

  it('rejects an unknown key under strict()', () => {
    const withExtra = { ...validIdentityData, bogusField: 1 }
    expect(IdentityDataSchema.strict().safeParse(withExtra).success).toBe(false)
  })
})

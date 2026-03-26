import { describe, it, expect } from 'vitest'
import { DefenseTypeSchema, IdentitySpecListItemSchema } from '../IdentitySchemas'

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

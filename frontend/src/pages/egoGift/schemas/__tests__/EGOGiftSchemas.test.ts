import { describe, it, expect } from 'vitest'
import {
  EGOGiftSpecSchema,
  EGOGiftDataSchema,
  EGOGiftI18nSchema,
} from '../EGOGiftSchemas'

describe('EGOGiftSpecSchema', () => {
  const validSpec = {
    tag: ['TIER_2'],
    keyword: 'Combustion',
    battleKeywordList: ['Combustion'],
    attributeType: 'CRIMSON',
    themePack: [],
    maxEnhancement: 2,
  }

  it('accepts a valid spec entry from egoGiftSpecList.json', () => {
    expect(EGOGiftSpecSchema.safeParse(validSpec).success).toBe(true)
  })

  it('rejects an unknown key', () => {
    const result = EGOGiftSpecSchema.strict().safeParse({
      ...validSpec,
      unexpected: true,
    })
    expect(result.success).toBe(false)
  })

  it('rejects a tag array without a TIER_ entry', () => {
    expect(
      EGOGiftSpecSchema.safeParse({ ...validSpec, tag: ['Combustion'] }).success
    ).toBe(false)
  })
})

describe('EGOGiftDataSchema', () => {
  const validData = {
    attributeType: 'CRIMSON',
    tag: ['TIER_2'],
    keyword: 'Combustion',
    price: 198,
    themePack: [],
    maxEnhancement: 2,
    battleKeywordList: ['Combustion'],
  }

  it('accepts a valid detail entry from egoGift/{id}.json', () => {
    expect(EGOGiftDataSchema.safeParse(validData).success).toBe(true)
  })

  it('rejects an unknown key', () => {
    const result = EGOGiftDataSchema.strict().safeParse({
      ...validData,
      unexpected: true,
    })
    expect(result.success).toBe(false)
  })
})

describe('EGOGiftI18nSchema', () => {
  const validI18n = {
    name: 'Combustion Gift',
    descs: ['Base description', 'Enhanced description'],
    obtain: 'Available from theme packs',
  }

  it('accepts a valid i18n entry', () => {
    expect(EGOGiftI18nSchema.safeParse(validI18n).success).toBe(true)
  })

  it('rejects an unknown key', () => {
    const result = EGOGiftI18nSchema.strict().safeParse({
      ...validI18n,
      unexpected: true,
    })
    expect(result.success).toBe(false)
  })
})

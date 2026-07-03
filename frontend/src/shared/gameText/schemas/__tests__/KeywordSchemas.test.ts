import { describe, it, expect } from 'vitest'
import {
  BattleKeywordSpecEntrySchema,
  BattleKeywordSpecListSchema,
  BattleKeywordNameListSchema,
} from '../KeywordSchemas'

describe('BattleKeywordSpecEntrySchema', () => {
  const validEntry = {
    iconId: 'Sinking',
    buffType: 'Negative',
    identities: ['10101', '10205'],
    egos: ['20301'],
    egoGifts: ['9001'],
  }

  it('accepts valid entry with all fields', () => {
    const result = BattleKeywordSpecEntrySchema.safeParse(validEntry)
    expect(result.success).toBe(true)
  })

  it('accepts entry with null iconId', () => {
    const result = BattleKeywordSpecEntrySchema.safeParse({
      ...validEntry,
      iconId: null,
    })
    expect(result.success).toBe(true)
  })

  it('accepts entry with empty arrays', () => {
    const result = BattleKeywordSpecEntrySchema.safeParse({
      iconId: null,
      buffType: 'Neutral',
      identities: [],
      egos: [],
      egoGifts: [],
    })
    expect(result.success).toBe(true)
  })

  it('rejects entry missing buffType', () => {
    const { buffType: _, ...incomplete } = validEntry
    const result = BattleKeywordSpecEntrySchema.safeParse(incomplete)
    expect(result.success).toBe(false)
  })

  it('rejects entry missing identities', () => {
    const { identities: _, ...incomplete } = validEntry
    const result = BattleKeywordSpecEntrySchema.safeParse(incomplete)
    expect(result.success).toBe(false)
  })

  it('rejects entry missing egos', () => {
    const { egos: _, ...incomplete } = validEntry
    const result = BattleKeywordSpecEntrySchema.safeParse(incomplete)
    expect(result.success).toBe(false)
  })

  it('rejects entry missing egoGifts', () => {
    const { egoGifts: _, ...incomplete } = validEntry
    const result = BattleKeywordSpecEntrySchema.safeParse(incomplete)
    expect(result.success).toBe(false)
  })

  it('rejects extra fields (strict mode)', () => {
    const result = BattleKeywordSpecEntrySchema.safeParse({
      ...validEntry,
      name: 'Sinking',
    })
    expect(result.success).toBe(false)
  })
})

describe('BattleKeywordSpecListSchema', () => {
  it('accepts valid record of spec entries', () => {
    const result = BattleKeywordSpecListSchema.safeParse({
      Sinking: {
        iconId: 'Sinking',
        buffType: 'Negative',
        identities: ['10101'],
        egos: [],
        egoGifts: [],
      },
      Burn: {
        iconId: 'Burn',
        buffType: 'Negative',
        identities: [],
        egos: ['20301'],
        egoGifts: [],
      },
    })
    expect(result.success).toBe(true)
  })

  it('accepts empty record', () => {
    const result = BattleKeywordSpecListSchema.safeParse({})
    expect(result.success).toBe(true)
  })

  it('rejects record with invalid entry', () => {
    const result = BattleKeywordSpecListSchema.safeParse({
      Sinking: { iconId: 'Sinking' },
    })
    expect(result.success).toBe(false)
  })
})

describe('BattleKeywordNameListSchema', () => {
  it('accepts valid string record', () => {
    const result = BattleKeywordNameListSchema.safeParse({
      Sinking: 'Sinking',
      Burn: 'Burn',
    })
    expect(result.success).toBe(true)
  })

  it('accepts empty record', () => {
    const result = BattleKeywordNameListSchema.safeParse({})
    expect(result.success).toBe(true)
  })

  it('rejects non-string values', () => {
    const result = BattleKeywordNameListSchema.safeParse({
      Sinking: 123,
    })
    expect(result.success).toBe(false)
  })
})

import { describe, it, expect } from 'vitest'
import {
  BattleKeywordEntrySchema,
  BattleKeywordsSchema,
} from '../BattleKeywordsSchemas'

describe('BattleKeywordEntrySchema', () => {
  it('accepts valid entry with name and desc', () => {
    const result = BattleKeywordEntrySchema.safeParse({
      name: 'Sinking',
      desc: 'Reduces SP at turn end.',
    })
    expect(result.success).toBe(true)
  })

  it('rejects entry missing name', () => {
    const result = BattleKeywordEntrySchema.safeParse({
      desc: 'Some description',
    })
    expect(result.success).toBe(false)
  })

  it('rejects entry missing desc', () => {
    const result = BattleKeywordEntrySchema.safeParse({
      name: 'Sinking',
    })
    expect(result.success).toBe(false)
  })

  it('rejects extra fields like iconId (strict mode)', () => {
    const result = BattleKeywordEntrySchema.safeParse({
      name: 'Sinking',
      desc: 'Reduces SP at turn end.',
      iconId: 'Sinking',
    })
    expect(result.success).toBe(false)
  })

  it('rejects extra fields like buffType (strict mode)', () => {
    const result = BattleKeywordEntrySchema.safeParse({
      name: 'Sinking',
      desc: 'Reduces SP at turn end.',
      buffType: 'Negative',
    })
    expect(result.success).toBe(false)
  })
})

describe('BattleKeywordsSchema', () => {
  it('accepts valid record of keyword entries', () => {
    const result = BattleKeywordsSchema.safeParse({
      Sinking: { name: 'Sinking', desc: 'Reduces SP at turn end.' },
      Burn: { name: 'Burn', desc: 'Deals damage at turn end.' },
    })
    expect(result.success).toBe(true)
  })

  it('accepts empty record', () => {
    const result = BattleKeywordsSchema.safeParse({})
    expect(result.success).toBe(true)
  })

  it('rejects record with invalid entry', () => {
    const result = BattleKeywordsSchema.safeParse({
      Sinking: { name: 'Sinking' },
    })
    expect(result.success).toBe(false)
  })
})

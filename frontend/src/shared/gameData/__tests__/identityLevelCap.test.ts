import { describe, it, expect } from 'vitest'

import { IDENTITY_LEVEL_CAP, IdentityLevelCapSchema } from '../identityLevelCap'

describe('IdentityLevelCapSchema', () => {
  it('accepts a positive integer cap', () => {
    expect(IdentityLevelCapSchema.parse({ maxLevel: 65 })).toEqual({ maxLevel: 65 })
  })

  it.each([
    ['a zero cap', { maxLevel: 0 }],
    ['a fractional cap', { maxLevel: 64.5 }],
    ['a missing cap', {}],
    ['an unknown key', { maxLevel: 65, minLevel: 1 }],
  ])('rejects %s', (_label, file) => {
    expect(IdentityLevelCapSchema.safeParse(file).success).toBe(false)
  })
})

describe('IDENTITY_LEVEL_CAP', () => {
  it('is the cap identityLevelCap.json ships', () => {
    expect(IDENTITY_LEVEL_CAP).toBe(65)
  })
})

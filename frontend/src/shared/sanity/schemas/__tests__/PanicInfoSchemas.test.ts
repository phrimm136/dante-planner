import { describe, expect, it } from 'vitest'

import { PanicInfoEntrySchema } from '../PanicInfoSchemas'

const entry = { name: 'Tremor', lowMoraleDesc: '', panicDesc: 'Does not act.' }

describe('PanicInfoEntrySchema', () => {
  it('accepts an entry with a flavor line', () => {
    expect(PanicInfoEntrySchema.parse({ ...entry, flavor: 'My hands shake...' }).flavor).toBe(
      'My hands shake...',
    )
  })

  it('accepts an entry without a flavor line', () => {
    expect(PanicInfoEntrySchema.parse(entry).flavor).toBeUndefined()
  })

  it('rejects an unknown key', () => {
    expect(PanicInfoEntrySchema.safeParse({ ...entry, flavour: 'x' }).success).toBe(false)
  })
})

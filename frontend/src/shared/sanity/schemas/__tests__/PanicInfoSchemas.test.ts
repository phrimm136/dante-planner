import { describe, expect, it } from 'vitest'

import panicInfoCN from '@static/i18n/CN/panicInfo.json'
import panicInfoEN from '@static/i18n/EN/panicInfo.json'
import panicInfoJP from '@static/i18n/JP/panicInfo.json'
import panicInfoKR from '@static/i18n/KR/panicInfo.json'
import { PanicInfoEntrySchema, PanicInfoSchema } from '../PanicInfoSchemas'

const entry = { name: 'Tremor', lowMoraleDesc: '', panicDesc: 'Does not act.', iconId: 9999 }

describe('PanicInfoEntrySchema', () => {
  it('accepts an entry with a flavor line', () => {
    expect(PanicInfoEntrySchema.parse({ ...entry, flavor: 'My hands shake...' }).flavor).toBe(
      'My hands shake...',
    )
  })

  it('accepts an entry without a flavor line', () => {
    expect(PanicInfoEntrySchema.parse(entry).flavor).toBeUndefined()
  })

  it('reads the icon id', () => {
    expect(PanicInfoEntrySchema.parse(entry).iconId).toBe(9999)
  })

  it('rejects an entry without an icon id', () => {
    const { iconId: _iconId, ...withoutIcon } = entry
    expect(PanicInfoEntrySchema.safeParse(withoutIcon).success).toBe(false)
  })

  it('rejects an unknown key', () => {
    expect(PanicInfoEntrySchema.safeParse({ ...entry, flavour: 'x' }).success).toBe(false)
  })
})

describe('panic icon ids', () => {
  it.each([
    ['KR', panicInfoKR],
    ['EN', panicInfoEN],
    ['JP', panicInfoJP],
    ['CN', panicInfoCN],
  ])('falls back to the 9999 icon for Vergilius 9997 and keeps 9994 in %s', (_, raw) => {
    const info = PanicInfoSchema.parse(raw)
    expect(info['9997']?.iconId).toBe(9999)
    expect(info['9994']?.iconId).toBe(9994)
  })
})

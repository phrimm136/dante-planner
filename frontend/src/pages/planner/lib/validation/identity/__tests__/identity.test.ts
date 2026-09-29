import { describe, it, expect } from 'vitest'

import { contentPath, field, key, renderContentPath } from '../../contentPath'
import { identityRuleViolations, parseIdentity } from '../identity'
import type { IdentityReferenceData } from '../identity'
import { rendered } from '../../__tests__/rendered'

const SINNER_PATH = contentPath(field('equipment'), key('01'))

const parse = (sinnerEquipment: Record<string, unknown>) =>
  parseIdentity(sinnerEquipment, SINNER_PATH, '01')

describe('parseIdentity', () => {
  it('admits an identity with an id', () => {
    const parsed = parse({ identity: { id: '10101', level: 1, uptie: 1 } })

    expect(parsed.violations).toEqual([])
    expect(parsed.identity?.id).toBe('10101')
    expect(renderContentPath(parsed.identity?.path ?? [])).toBe('equipment[01].identity.id')
  })

  it.each([
    ['absent', {}],
    ['null', { identity: null }],
  ])('reports an identity that is %s as MISSING_IDENTITY', (_label, sinnerEquipment) => {
    expect(rendered(parse(sinnerEquipment).violations)).toEqual([
      { code: 'MISSING_IDENTITY', path: 'equipment[01].identity' },
    ])
  })

  it.each([
    ['a number', 5],
    ['a string', 'x'],
    ['an array', []],
  ])('reports an identity that is %s as INVALID_FIELD_TYPE', (_label, identity) => {
    expect(rendered(parse({ identity }).violations)).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'equipment[01].identity' },
    ])
  })

  it.each([
    ['absent', {}],
    ['null', { id: null }],
    ['empty', { id: '' }],
    ['blank', { id: '  ' }],
  ])('reports an id that is %s as MISSING_IDENTITY at the id', (_label, identity) => {
    expect(rendered(parse({ identity }).violations)).toEqual([
      { code: 'MISSING_IDENTITY', path: 'equipment[01].identity.id' },
    ])
  })

  it('reports a numeric id as INVALID_FIELD_TYPE at the id', () => {
    expect(rendered(parse({ identity: { id: 10101 } }).violations)).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'equipment[01].identity.id' },
    ])
  })
})

describe('identityRuleViolations', () => {
  const knows = (ids: string[]) => ({ hasIdentity: (id: string) => ids.includes(id) })
  const knowsAll: IdentityReferenceData = { hasIdentity: () => true }

  const rulesOf = (identity: Record<string, unknown>, data = knowsAll) =>
    rendered(identityRuleViolations(parse({ identity }), data))

  it('passes a known identity of its sinner within the level and uptie bounds', () => {
    expect(rulesOf({ id: '10101', level: 60, uptie: 4 })).toEqual([])
    expect(rulesOf({ id: '10101', level: 1, uptie: 1 })).toEqual([])
    expect(rulesOf({ id: '10101' })).toEqual([])
  })

  it('reports an unknown id with the id as the message parameter, and nothing else', () => {
    expect(rulesOf({ id: '10299', level: 99 }, knows(['10101']))).toEqual([
      { code: 'UNKNOWN_IDENTITY', path: 'equipment[01].identity.id', params: { id: '10299' } },
    ])
  })

  it.each(['10201', '1010', 'abc'])(
    'reports the identity %s that does not belong to sinner 01, and nothing else',
    (id) => {
      expect(rulesOf({ id, level: 99 })).toEqual([
        { code: 'MISMATCHED_IDENTITY', path: 'equipment[01].identity.id' },
      ])
    },
  )

  it('accepts an id of either sinner-scoped kind, as the backend does', () => {
    expect(rulesOf({ id: '20101' })).toEqual([])
  })

  it.each([
    ['level', 0, 'OUT_OF_RANGE_IDENTITY_LEVEL'],
    ['level', 61, 'OUT_OF_RANGE_IDENTITY_LEVEL'],
    ['uptie', 0, 'OUT_OF_RANGE_IDENTITY_UPTIE'],
    ['uptie', 5, 'OUT_OF_RANGE_IDENTITY_UPTIE'],
  ] as const)('reports %s %d at the field', (name, value, code) => {
    expect(rulesOf({ id: '10101', [name]: value })).toEqual([
      { code, path: `equipment[01].identity.${name}` },
    ])
  })

  it.each([
    ['level', '3'],
    ['uptie', 1.5],
    ['level', null],
  ] as const)('reports %s %j of the wrong kind as INVALID_FIELD_TYPE', (name, value) => {
    expect(rulesOf({ id: '10101', [name]: value })).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: `equipment[01].identity.${name}` },
    ])
  })

  it('skips an identity the boundary rejected', () => {
    expect(identityRuleViolations(parse({}), knows([]))).toEqual([])
  })
})

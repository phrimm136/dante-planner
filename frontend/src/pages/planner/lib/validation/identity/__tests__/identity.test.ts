import { describe, it, expect } from 'vitest'

import { contentPath, field, key } from '../../contentPath'
import type { SinnerEquipmentValue } from '../../contentShape'
import type { SinnerEntry } from '../../sinnerEntries'
import { identityRuleViolations } from '../identity'
import type { IdentityReferenceData } from '../identity'
import { rendered } from '../../__tests__/rendered'

const SINNER_PATH = contentPath(field('equipment'), key('1'))

const entryOf = (identity: { id: string; level: number; uptie: number }) =>
  ({
    sinnerKey: '1',
    path: SINNER_PATH,
    value: { identity, egos: { ZAYIN: { id: '20101', threadspin: 1 } } },
  }) as unknown as SinnerEntry<SinnerEquipmentValue>

describe('identityRuleViolations', () => {
  const knows = (ids: string[]) => ({ hasIdentity: (id: string) => ids.includes(id) })
  const knowsAll: IdentityReferenceData = { hasIdentity: () => true }

  const rulesOf = (identity: { id: string; level?: number; uptie?: number }, data = knowsAll) =>
    rendered(identityRuleViolations(entryOf({ level: 1, uptie: 1, ...identity }), data))

  it('passes a known identity of its sinner within the level and uptie bounds', () => {
    expect(rulesOf({ id: '10101', level: 60, uptie: 4 })).toEqual([])
    expect(rulesOf({ id: '10101', level: 1, uptie: 1 })).toEqual([])
  })

  it('reports an unknown id with the id as the message parameter, and nothing else', () => {
    expect(rulesOf({ id: '10299', level: 99 }, knows(['10101']))).toEqual([
      { code: 'UNKNOWN_IDENTITY', path: 'equipment[1].identity.id', params: { id: '10299' } },
    ])
  })

  it('reports an identity of another sinner, and nothing else', () => {
    expect(rulesOf({ id: '10201', level: 99 })).toEqual([
      { code: 'MISMATCHED_IDENTITY', path: 'equipment[1].identity.id' },
    ])
  })

  it('matches a two-digit sinner key against the id digits', () => {
    const entry = { ...entryOf({ id: '11201', level: 1, uptie: 1 }), sinnerKey: '12' }
    expect(identityRuleViolations(entry, knowsAll)).toEqual([])
  })

  it.each([
    ['level', 0, 'OUT_OF_RANGE_IDENTITY_LEVEL'],
    ['level', 61, 'OUT_OF_RANGE_IDENTITY_LEVEL'],
    ['uptie', 0, 'OUT_OF_RANGE_IDENTITY_UPTIE'],
    ['uptie', 5, 'OUT_OF_RANGE_IDENTITY_UPTIE'],
  ] as const)('reports %s %d at the field', (name, value, code) => {
    expect(rulesOf({ id: '10101', [name]: value })).toEqual([
      { code, path: `equipment[1].identity.${name}` },
    ])
  })
})

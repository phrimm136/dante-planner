import { describe, it, expect } from 'vitest'

import { contentPath, field, key } from '../../contentPath'
import { egoRuleViolations, parseEgos } from '../egos'
import type { EgoReferenceData } from '../egos'
import { rendered } from '../../__tests__/rendered'

const SINNER_PATH = contentPath(field('equipment'), key('01'))

const parse = (sinnerEquipment: Record<string, unknown>) =>
  parseEgos(sinnerEquipment, SINNER_PATH, '01')

const violationsOf = (egos: unknown) => rendered(parse({ egos }).violations)

describe('parseEgos', () => {
  it('admits every slot with an id', () => {
    const parsed = parse({ egos: { ZAYIN: { id: '20101' }, TETH: { id: '20102' } } })

    expect(parsed.violations).toEqual([])
    expect(rendered(parsed.egos).map(({ egoType, id, path }) => ({ egoType, id, path }))).toEqual([
      { egoType: 'ZAYIN', id: '20101', path: 'equipment[01].egos.ZAYIN.id' },
      { egoType: 'TETH', id: '20102', path: 'equipment[01].egos.TETH.id' },
    ])
  })

  it.each([
    ['absent', undefined],
    ['null', null],
  ])('reports egos that are %s as MISSING_EGO_ZAYIN', (_label, egos) => {
    expect(violationsOf(egos)).toEqual([{ code: 'MISSING_EGO_ZAYIN', path: 'equipment[01].egos' }])
  })

  it.each([
    ['a number', 5],
    ['a string', 'x'],
    ['an array', []],
  ])('reports egos that are %s as INVALID_FIELD_TYPE', (_label, egos) => {
    expect(violationsOf(egos)).toEqual([{ code: 'INVALID_FIELD_TYPE', path: 'equipment[01].egos' }])
  })

  it.each([
    ['absent', {}],
    ['null', { ZAYIN: null }],
  ])('reports a ZAYIN slot that is %s as MISSING_EGO_ZAYIN at the slot', (_label, egos) => {
    expect(violationsOf(egos)).toEqual([
      { code: 'MISSING_EGO_ZAYIN', path: 'equipment[01].egos.ZAYIN' },
    ])
  })

  it('reports a ZAYIN slot of the wrong kind as INVALID_FIELD_TYPE at the slot', () => {
    expect(violationsOf({ ZAYIN: 'z' })).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'equipment[01].egos.ZAYIN' },
    ])
  })

  it.each([
    ['absent', {}],
    ['null', { id: null }],
    ['blank', { id: ' ' }],
  ])('reports a ZAYIN id that is %s as MISSING_EGO_ZAYIN at the id', (_label, zayin) => {
    expect(violationsOf({ ZAYIN: zayin })).toEqual([
      { code: 'MISSING_EGO_ZAYIN', path: 'equipment[01].egos.ZAYIN.id' },
    ])
  })

  it('reports a numeric id once, as INVALID_FIELD_TYPE, in any slot', () => {
    expect(violationsOf({ ZAYIN: { id: 20101 }, TETH: { id: 20102 } })).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'equipment[01].egos.TETH.id' },
      { code: 'INVALID_FIELD_TYPE', path: 'equipment[01].egos.ZAYIN.id' },
    ])
  })

  it('reports each slot key outside the E.G.O types at that key', () => {
    expect(violationsOf({ ZAYIN: { id: '20101' }, FOO: { id: '20102' }, BAR: {} })).toEqual([
      { code: 'INVALID_EGO_TYPE', path: 'equipment[01].egos.BAR' },
      { code: 'INVALID_EGO_TYPE', path: 'equipment[01].egos.FOO' },
    ])
  })

  it('leaves an optional slot without an id alone', () => {
    expect(violationsOf({ ZAYIN: { id: '20101' }, TETH: {}, HE: null, WAW: 3 })).toEqual([])
  })
})

describe('egoRuleViolations', () => {
  const knows = (ids: string[]) => ({ hasEgo: (id: string) => ids.includes(id) })
  const knowsAll: EgoReferenceData = { hasEgo: () => true }

  const rulesOf = (egos: Record<string, unknown>, data = knowsAll) =>
    rendered(egoRuleViolations(parse({ egos }), data))

  it('passes known E.G.O of their sinner within the threadspin bounds', () => {
    expect(
      rulesOf({ ZAYIN: { id: '20101', threadspin: 1 }, TETH: { id: '20102', threadspin: 5 } }),
    ).toEqual([])
  })

  it('reports each unknown id with the id as the message parameter, and nothing else', () => {
    expect(
      rulesOf({ ZAYIN: { id: '20199', threadspin: 9 }, TETH: { id: '20102' } }, knows(['20102'])),
    ).toEqual([
      { code: 'UNKNOWN_EGO', path: 'equipment[01].egos.ZAYIN.id', params: { id: '20199' } },
    ])
  })

  it('checks ids under a slot key outside the E.G.O types too', () => {
    expect(rulesOf({ ZAYIN: { id: '20101' }, FOO: { id: '20199' } }, knows(['20101']))).toEqual([
      { code: 'UNKNOWN_EGO', path: 'equipment[01].egos.FOO.id', params: { id: '20199' } },
    ])
  })

  it('reports an E.G.O of another sinner, and nothing else', () => {
    expect(rulesOf({ ZAYIN: { id: '20201', threadspin: 9 } })).toEqual([
      { code: 'MISMATCHED_EGO', path: 'equipment[01].egos.ZAYIN.id' },
    ])
  })

  it.each([0, 6])('reports threadspin %d at the field', (threadspin) => {
    expect(rulesOf({ ZAYIN: { id: '20101', threadspin } })).toEqual([
      { code: 'OUT_OF_RANGE_EGO_THREADSPIN', path: 'equipment[01].egos.ZAYIN.threadspin' },
    ])
  })

  it.each(['3', null, 2.5])(
    'reports threadspin %j of the wrong kind as INVALID_FIELD_TYPE',
    (threadspin) => {
      expect(rulesOf({ ZAYIN: { id: '20101', threadspin } })).toEqual([
        { code: 'INVALID_FIELD_TYPE', path: 'equipment[01].egos.ZAYIN.threadspin' },
      ])
    },
  )
})

import { describe, it, expect } from 'vitest'

import { contentPath, field, key } from '../../contentPath'
import type { SinnerEquipmentValue } from '../../contentShape'
import type { SinnerEntry } from '../../sinnerEntries'
import { egoRuleViolations } from '../egos'
import type { EgoReferenceData } from '../egos'
import { rendered } from '../../__tests__/rendered'

const SINNER_PATH = contentPath(field('equipment'), key('1'))

type Ego = { id: string; threadspin: number }

const entryOf = (egos: Record<string, Ego>) =>
  ({
    sinnerKey: '1',
    path: SINNER_PATH,
    value: { identity: { id: '10101', level: 1, uptie: 1 }, egos },
  }) as unknown as SinnerEntry<SinnerEquipmentValue>

describe('egoRuleViolations', () => {
  const knows = (ids: string[]): EgoReferenceData => ({
    hasEgo: (id) => ids.includes(id),
    maxThreadspin: () => 5,
  })
  const knowsAll: EgoReferenceData = { hasEgo: () => true, maxThreadspin: () => 5 }

  const rulesOf = (egos: Record<string, Ego>, data = knowsAll) =>
    rendered(egoRuleViolations(entryOf(egos), data))

  it('passes known E.G.O of their sinner within the threadspin bounds', () => {
    expect(
      rulesOf({ ZAYIN: { id: '20101', threadspin: 1 }, TETH: { id: '20102', threadspin: 5 } }),
    ).toEqual([])
  })

  it('reports each unknown id with the id as the message parameter, and nothing else', () => {
    expect(
      rulesOf(
        { ZAYIN: { id: '20199', threadspin: 9 }, TETH: { id: '20102', threadspin: 1 } },
        knows(['20102']),
      ),
    ).toEqual([
      { code: 'UNKNOWN_EGO', path: 'equipment[1].egos.ZAYIN.id', params: { id: '20199' } },
    ])
  })

  it('reports an E.G.O of another sinner, and nothing else', () => {
    expect(rulesOf({ ZAYIN: { id: '20201', threadspin: 9 } })).toEqual([
      { code: 'MISMATCHED_EGO', path: 'equipment[1].egos.ZAYIN.id' },
    ])
  })

  it('skips an optional slot left empty', () => {
    expect(
      egoRuleViolations(
        entryOf({ ZAYIN: { id: '20101', threadspin: 1 }, TETH: undefined as unknown as Ego }),
        knowsAll,
      ),
    ).toEqual([])
  })

  it.each([0, 6])('reports threadspin %d at the field', (threadspin) => {
    expect(rulesOf({ ZAYIN: { id: '20101', threadspin } })).toEqual([
      { code: 'OUT_OF_RANGE_EGO_THREADSPIN', path: 'equipment[1].egos.ZAYIN.threadspin' },
    ])
  })

  it('bounds threadspin by the E.G.O ceiling', () => {
    const ceilings: EgoReferenceData = {
      hasEgo: () => true,
      maxThreadspin: (id) => (id === '20102' ? 3 : 5),
    }
    expect(
      rulesOf(
        { ZAYIN: { id: '20101', threadspin: 5 }, TETH: { id: '20102', threadspin: 4 } },
        ceilings,
      ),
    ).toEqual([{ code: 'OUT_OF_RANGE_EGO_THREADSPIN', path: 'equipment[1].egos.TETH.threadspin' }])
    expect(
      rulesOf(
        { ZAYIN: { id: '20101', threadspin: 1 }, TETH: { id: '20102', threadspin: 3 } },
        ceilings,
      ),
    ).toEqual([])
  })

  it('reports a known E.G.O without a ceiling as MISSING_EGO_THREADSPIN_CEILING', () => {
    const noCeiling: EgoReferenceData = { hasEgo: () => true, maxThreadspin: () => undefined }
    expect(rulesOf({ ZAYIN: { id: '20101', threadspin: 1 } }, noCeiling)).toEqual([
      { code: 'MISSING_EGO_THREADSPIN_CEILING', path: 'equipment[1].egos.ZAYIN.threadspin' },
    ])
  })

  it('reports a threadspin outside 1-5 as out of range before looking up the ceiling', () => {
    const noCeiling: EgoReferenceData = { hasEgo: () => true, maxThreadspin: () => undefined }
    expect(rulesOf({ ZAYIN: { id: '20101', threadspin: 9 } }, noCeiling)).toEqual([
      { code: 'OUT_OF_RANGE_EGO_THREADSPIN', path: 'equipment[1].egos.ZAYIN.threadspin' },
    ])
  })
})

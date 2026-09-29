import { describe, it, expect } from 'vitest'

import { parseStartBuffs, startBuffReferenceViolations, startBuffViolations } from '../startBuffs'
import { rendered } from '../../__tests__/rendered'

const violationsOf = (raw: unknown) => rendered(startBuffViolations(parseStartBuffs(raw)))

describe('startBuffViolations', () => {
  it('passes buffs with distinct base ids', () => {
    expect(violationsOf([100, 201, 302])).toEqual([])
  })

  it('passes an empty list and an absent one', () => {
    expect(violationsOf([])).toEqual([])
    expect(violationsOf(undefined)).toEqual([])
  })

  it('reports more than 10 buffs at the list', () => {
    expect(violationsOf([100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 100])).toEqual([
      { code: 'EXCESS_STARTBUFF', path: 'selectedBuffIds' },
      { code: 'DUPLICATE_STARTBUFF', path: 'selectedBuffIds[10]' },
    ])
  })

  it('reports a base id outside 0-9 at the buff', () => {
    expect(violationsOf([110])).toEqual([
      { code: 'OUT_OF_RANGE_STARTBUFF', path: 'selectedBuffIds[0]' },
    ])
  })

  it('reports a repeated base id at the later buff', () => {
    expect(violationsOf([100, 200])).toEqual([
      { code: 'DUPLICATE_STARTBUFF', path: 'selectedBuffIds[1]' },
    ])
  })

  it('does not count an out-of-range base id as a repeat', () => {
    expect(violationsOf([110, 210])).toEqual([
      { code: 'OUT_OF_RANGE_STARTBUFF', path: 'selectedBuffIds[0]' },
      { code: 'OUT_OF_RANGE_STARTBUFF', path: 'selectedBuffIds[1]' },
    ])
  })

  it.each([
    ['a string', '101'],
    ['a fraction', 100.5],
    ['null', null],
  ])('reports a buff that is %s as INVALID_FIELD_TYPE', (_label, buff) => {
    expect(violationsOf([100, buff])).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'selectedBuffIds[1]' },
    ])
  })

  it('reports a list of the wrong kind as INVALID_FIELD_TYPE', () => {
    expect(violationsOf(null)).toEqual([{ code: 'INVALID_FIELD_TYPE', path: 'selectedBuffIds' }])
  })
})

describe('startBuffReferenceViolations', () => {
  it('reports each unknown buff with its id as the message parameter', () => {
    const data = { hasStartBuff: (id: string) => id === '100' }

    expect(rendered(startBuffReferenceViolations(parseStartBuffs([100, 201]), data))).toEqual([
      { code: 'UNKNOWN_STARTBUFF', path: 'selectedBuffIds[1]', params: { id: '201' } },
    ])
  })
})

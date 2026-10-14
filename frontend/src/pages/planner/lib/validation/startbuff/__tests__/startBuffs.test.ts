import { describe, it, expect } from 'vitest'

import md7StartBuffs from '@static/data/MD7/startBuffs.json'
import { startBuffBaseIds, startBuffViolations } from '../startBuffs'
import type { StartBuffSeason } from '../startBuffs'
import { rendered } from '../../__tests__/rendered'

const TEN_BASES = startBuffBaseIds([
  '100',
  '101',
  '102',
  '103',
  '104',
  '105',
  '106',
  '107',
  '108',
  '109',
])

const KNOWS_ALL: StartBuffSeason = { hasStartBuff: () => true, startBuffBaseIds: TEN_BASES }

const violationsOf = (buffIds: number[], season: StartBuffSeason = KNOWS_ALL) =>
  rendered(startBuffViolations(buffIds, season))

describe('startBuffViolations', () => {
  it('passes buffs with distinct base ids', () => {
    expect(violationsOf([100, 201, 302])).toEqual([])
  })

  it('passes an empty list', () => {
    expect(violationsOf([])).toEqual([])
  })

  it('reports more than 10 buffs at the list', () => {
    expect(violationsOf([100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 100])).toEqual([
      { code: 'EXCESS_STARTBUFF', path: 'selectedBuffIds' },
      { code: 'DUPLICATE_STARTBUFF', path: 'selectedBuffIds[10]' },
    ])
  })

  it('reports a repeated base id at the later buff', () => {
    expect(violationsOf([100, 200])).toEqual([
      { code: 'DUPLICATE_STARTBUFF', path: 'selectedBuffIds[1]' },
    ])
  })
})

describe('unknown start buffs', () => {
  const knows = (ids: string[]): StartBuffSeason => ({
    hasStartBuff: (id) => ids.includes(id),
    startBuffBaseIds: TEN_BASES,
  })

  it('reports each unknown buff with its id as the message parameter', () => {
    expect(violationsOf([100, 201], knows(['100']))).toEqual([
      { code: 'UNKNOWN_STARTBUFF', path: 'selectedBuffIds[1]', params: { id: '201' } },
    ])
  })

  it('reports an unknown buff once, without its base-id range', () => {
    expect(violationsOf([999], knows(['100']))).toEqual([
      { code: 'UNKNOWN_STARTBUFF', path: 'selectedBuffIds[0]', params: { id: '999' } },
    ])
  })

  it('does not count an unknown buff as a repeated base id', () => {
    expect(violationsOf([100, 900], knows(['100']))).toEqual([
      { code: 'UNKNOWN_STARTBUFF', path: 'selectedBuffIds[1]', params: { id: '900' } },
    ])
  })

  it('reports more than 10 buffs beside every per-buff violation', () => {
    const buffs = [100, 101, 102, 103, 104, 105, 106, 107, 108, 999, 910]
    expect(violationsOf(buffs, knows(buffs.map(String).filter((id) => id !== '999')))).toEqual([
      { code: 'EXCESS_STARTBUFF', path: 'selectedBuffIds' },
      { code: 'UNKNOWN_STARTBUFF', path: 'selectedBuffIds[9]', params: { id: '999' } },
    ])
  })
})

describe('start buff limits follow the season', () => {
  const ELEVEN_IDS = [100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110]
  const MD7: StartBuffSeason = {
    hasStartBuff: (buffId) => buffId in md7StartBuffs,
    startBuffBaseIds: startBuffBaseIds(Object.keys(md7StartBuffs)),
  }

  it('accepts 11 buffs in a season with ids 100 to 110', () => {
    const season: StartBuffSeason = {
      hasStartBuff: () => true,
      startBuffBaseIds: startBuffBaseIds(ELEVEN_IDS.map(String)),
    }

    expect(violationsOf(ELEVEN_IDS, season)).toEqual([])
  })

  it('accepts 10 buffs in MD7', () => {
    expect(violationsOf(ELEVEN_IDS.slice(0, 10), MD7)).toEqual([])
  })

  it('rejects 110 as UNKNOWN_STARTBUFF and an 11th buff as EXCESS_STARTBUFF in MD7', () => {
    expect(violationsOf(ELEVEN_IDS, MD7)).toEqual([
      { code: 'EXCESS_STARTBUFF', path: 'selectedBuffIds' },
      { code: 'UNKNOWN_STARTBUFF', path: 'selectedBuffIds[10]', params: { id: '110' } },
    ])
  })
})

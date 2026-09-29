import { describe, it, expect } from 'vitest'

import { contentPath, field, renderContentPath } from '../../contentPath'
import { parseSinners } from '../sinnerKeys'

const EQUIPMENT = contentPath(field('equipment'))

const ALL_KEYS = Array.from({ length: 12 }, (_, i) => String(i + 1).padStart(2, '0'))

const allSinners = (): Record<string, unknown> =>
  Object.fromEntries(ALL_KEYS.map((sinnerKey) => [sinnerKey, { sinnerKey }]))

const parse = (raw: unknown) => {
  const parsed = parseSinners(raw, EQUIPMENT)
  return {
    entries: parsed.entries.map((entry) => [entry.sinnerKey, renderContentPath(entry.path)]),
    violations: parsed.violations.map((violation) => ({
      code: violation.code,
      path: renderContentPath(violation.path),
    })),
  }
}

describe('parseSinners', () => {
  it('admits all 12 sinners in key order with no violations', () => {
    const { entries, violations } = parse(allSinners())

    expect(violations).toEqual([])
    expect(entries).toEqual(ALL_KEYS.map((sinnerKey) => [sinnerKey, `equipment[${sinnerKey}]`]))
  })

  it('reports the missing sinners once at the container', () => {
    const sinners = allSinners()
    delete sinners['01']
    delete sinners['07']

    const parsed = parseSinners(sinners, EQUIPMENT)

    expect(parsed.violations).toEqual([
      { code: 'MISSING_SINNER', path: EQUIPMENT, missingSinners: ['01', '07'] },
    ])
    expect(parsed.entries).toHaveLength(10)
  })

  it('reads an absent container as one with no sinners', () => {
    expect(parseSinners(undefined, EQUIPMENT).violations).toEqual([
      { code: 'MISSING_SINNER', path: EQUIPMENT, missingSinners: ALL_KEYS },
    ])
  })

  it.each([
    ['null', null],
    ['a number', 42],
    ['a string', 'abc'],
    ['an array', []],
  ])('reports a container that is %s as INVALID_FIELD_TYPE and admits nothing', (_label, raw) => {
    expect(parse(raw)).toEqual({
      entries: [],
      violations: [{ code: 'INVALID_FIELD_TYPE', path: 'equipment' }],
    })
  })

  it.each([
    ['null', null],
    ['a number', 5],
    ['a string', 'x'],
    ['an array', []],
  ])('reports a sinner entry that is %s as INVALID_FIELD_TYPE at its key', (_label, value) => {
    const sinners = { ...allSinners(), '01': value }

    const { entries, violations } = parse(sinners)

    expect(violations).toEqual([{ code: 'INVALID_FIELD_TYPE', path: 'equipment[01]' }])
    expect(entries.map(([sinnerKey]) => sinnerKey)).not.toContain('01')
  })

  it('reads an unpadded key as its sinner and prefers the padded spelling', () => {
    const sinners = allSinners()
    delete sinners['03']
    const padded = { ...sinners, '3': { spelling: 'unpadded' } }
    const both = { ...allSinners(), '3': { spelling: 'unpadded' } }

    expect(parseSinners(padded, EQUIPMENT).entries[2]?.value).toEqual({ spelling: 'unpadded' })
    expect(parseSinners(both, EQUIPMENT).entries[2]?.value).toEqual({ sinnerKey: '03' })
  })

  it.each(['abc', '', '1.5', ' 2', '0x2'])(
    'reports the key %j that is not an integer as INVALID_FIELD_TYPE',
    (key) => {
      expect(parse({ ...allSinners(), [key]: {} }).violations).toEqual([
        { code: 'INVALID_FIELD_TYPE', path: `equipment[${key}]` },
      ])
    },
  )

  it.each(['0', '13', '-1', '+13'])(
    'reports the key %j outside 1-12 as OUT_OF_RANGE_SINNER',
    (key) => {
      expect(parse({ ...allSinners(), [key]: {} }).violations).toEqual([
        { code: 'OUT_OF_RANGE_SINNER', path: `equipment[${key}]` },
      ])
    },
  )

  it('reports a bad key beside the sinners it leaves missing', () => {
    const sinners = allSinners()
    delete sinners['12']

    expect(parse({ ...sinners, '13': {} }).violations).toEqual([
      { code: 'MISSING_SINNER', path: 'equipment' },
      { code: 'OUT_OF_RANGE_SINNER', path: 'equipment[13]' },
    ])
  })
})

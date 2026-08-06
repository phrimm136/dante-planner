/**
 * searchTerms.test.ts
 *
 * Table tests for the search predicate and the keyword reverse-lookup the slice term
 * builders share.
 */

import { describe, it, expect } from 'vitest'
import { collectKeywordTerms, matchesSearch } from '../searchTerms'

describe('matchesSearch', () => {
  const TERMS = ['gregor', 'rupture', 'blade lineage']

  const CASES: { name: string; query: string; terms: readonly string[]; expected: boolean }[] = [
    { name: 'empty query matches', query: '', terms: TERMS, expected: true },
    { name: 'empty query matches with no terms', query: '', terms: [], expected: true },
    { name: 'no terms never matches a query', query: 'gregor', terms: [], expected: false },
    { name: 'exact term', query: 'rupture', terms: TERMS, expected: true },
    { name: 'substring of a term', query: 'upt', terms: TERMS, expected: true },
    { name: 'prefix of a term', query: 'blade', terms: TERMS, expected: true },
    { name: 'query is uppercased', query: 'RUPTURE', terms: TERMS, expected: true },
    { name: 'query is mixed case', query: 'Blade Lineage', terms: TERMS, expected: true },
    { name: 'unrelated query', query: 'sinking', terms: TERMS, expected: false },
    { name: 'query longer than every term', query: 'ruptured', terms: TERMS, expected: false },
    { name: 'term is a lone empty string', query: 'a', terms: [''], expected: false },
    { name: 'empty term matched by empty query', query: '', terms: [''], expected: true },
  ]

  it.each(CASES)('$name', ({ query, terms, expected }) => {
    expect(matchesSearch(query, terms)).toBe(expected)
  })

  it('does not lowercase the terms it is given', () => {
    expect(matchesSearch('gregor', ['Gregor'])).toBe(false)
  })
})

describe('collectKeywordTerms', () => {
  const REVERSE_MAP = new Map<string, string[]>([
    ['rupture', ['Burst']],
    ['burn', ['Combustion']],
    ['charge', ['Charge']],
    ['tremor', ['Vibration', 'Tremor']],
  ])

  const CASES: {
    name: string
    carried: readonly string[]
    expected: string[]
  }[] = [
    { name: 'carries nothing', carried: [], expected: [] },
    { name: 'carries one code', carried: ['Burst'], expected: ['rupture'] },
    { name: 'carries two codes', carried: ['Burst', 'Charge'], expected: ['rupture', 'charge'] },
    {
      name: 'reports map order, not carrier order',
      carried: ['Charge', 'Burst'],
      expected: ['rupture', 'charge'],
    },
    { name: 'one bucket entry is enough', carried: ['Tremor'], expected: ['tremor'] },
    {
      name: 'both bucket entries yield one term',
      carried: ['Vibration', 'Tremor'],
      expected: ['tremor'],
    },
    { name: 'unmapped code contributes nothing', carried: ['Poise'], expected: [] },
    {
      name: 'carries every mapped code',
      carried: ['Burst', 'Combustion', 'Charge', 'Vibration'],
      expected: ['rupture', 'burn', 'charge', 'tremor'],
    },
  ]

  it.each(CASES)('$name', ({ carried, expected }) => {
    expect(collectKeywordTerms(REVERSE_MAP, (code) => carried.includes(code))).toEqual(expected)
  })

  it('returns nothing for an empty map', () => {
    expect(collectKeywordTerms(new Map(), () => true)).toEqual([])
  })

  it('skips entries whose bucket is empty', () => {
    const withEmptyBucket = new Map<string, string[]>([
      ['rupture', []],
      ['burn', ['Combustion']],
    ])

    expect(collectKeywordTerms(withEmptyBucket, () => true)).toEqual(['burn'])
  })
})

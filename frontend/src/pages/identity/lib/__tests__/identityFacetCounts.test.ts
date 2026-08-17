/**
 * identityFacetCounts.test.ts
 *
 * Table tests for the dropdown tallies: how seasons and unit keywords accumulate across
 * a spec, and what an empty or keyword-less spec yields.
 */

import { describe, it, expect } from 'vitest'
import { buildFacetCounts } from '../identityFacetCounts'

interface Entry {
  season: number
  unitKeywordList: string[]
}

function spec(...entries: Entry[]): Record<string, Entry> {
  return Object.fromEntries(entries.map((entry, index) => [`1010${String(index)}`, entry]))
}

const CASES: {
  name: string
  spec: Record<string, Entry>
  seasonCounts: Record<string, number>
  unitKeywordCounts: Record<string, number>
}[] = [
  {
    name: 'empty spec tallies nothing',
    spec: {},
    seasonCounts: {},
    unitKeywordCounts: {},
  },
  {
    name: 'a lone identity',
    spec: spec({ season: 1, unitKeywordList: ['BladeLineage'] }),
    seasonCounts: { '1': 1 },
    unitKeywordCounts: { BladeLineage: 1 },
  },
  {
    name: 'identities sharing a season',
    spec: spec(
      { season: 2, unitKeywordList: [] },
      { season: 2, unitKeywordList: [] },
      { season: 3, unitKeywordList: [] },
    ),
    seasonCounts: { '2': 2, '3': 1 },
    unitKeywordCounts: {},
  },
  {
    name: 'one identity carrying several keywords',
    spec: spec({ season: 1, unitKeywordList: ['BladeLineage', 'SevenAssociation'] }),
    seasonCounts: { '1': 1 },
    unitKeywordCounts: { BladeLineage: 1, SevenAssociation: 1 },
  },
  {
    name: 'keywords accumulate across identities',
    spec: spec(
      { season: 1, unitKeywordList: ['BladeLineage'] },
      { season: 4, unitKeywordList: ['BladeLineage', 'Zwei'] },
      { season: 4, unitKeywordList: ['Zwei'] },
    ),
    seasonCounts: { '1': 1, '4': 2 },
    unitKeywordCounts: { BladeLineage: 2, Zwei: 2 },
  },
  {
    name: 'identities with no keywords still count toward their season',
    spec: spec({ season: 7, unitKeywordList: [] }, { season: 7, unitKeywordList: ['Zwei'] }),
    seasonCounts: { '7': 2 },
    unitKeywordCounts: { Zwei: 1 },
  },
]

describe('buildFacetCounts', () => {
  it.each(CASES)('$name', ({ spec: input, seasonCounts, unitKeywordCounts }) => {
    expect(buildFacetCounts(input)).toEqual({ seasonCounts, unitKeywordCounts })
  })

  it('keys seasons by their string form', () => {
    const { seasonCounts } = buildFacetCounts(spec({ season: 10, unitKeywordList: [] }))
    expect(Object.keys(seasonCounts)).toEqual(['10'])
  })

  it('does not mutate the spec it reads', () => {
    const input = spec({ season: 1, unitKeywordList: ['BladeLineage'] })
    const before = structuredClone(input)

    buildFacetCounts(input)

    expect(input).toEqual(before)
  })
})

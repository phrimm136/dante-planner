/**
 * applyFacets.test.ts
 *
 * Unit tests for the facet evaluator itself: how one facet reads an item, how the two
 * modes decide, and how facets compose.
 */

import { describe, it, expect } from 'vitest'
import { applyFacets, type Facet } from '../applyFacets'

interface Item {
  rank: number
  keywords: string[]
  season?: number
}

interface State {
  ranks: ReadonlySet<number>
  keywordsAny: ReadonlySet<string>
  keywordsAll: ReadonlySet<string>
  seasons: ReadonlySet<number>
}

const EMPTY_STATE: State = {
  ranks: new Set(),
  keywordsAny: new Set(),
  keywordsAll: new Set(),
  seasons: new Set(),
}

const RANK: Facet<Item, State> = { sel: (s) => s.ranks, get: (i) => i.rank, mode: 'any' }
const KEYWORDS_ANY: Facet<Item, State> = {
  sel: (s) => s.keywordsAny,
  get: (i) => i.keywords,
  mode: 'any',
}
const KEYWORDS_ALL: Facet<Item, State> = {
  sel: (s) => s.keywordsAll,
  get: (i) => i.keywords,
  mode: 'all',
}
const SEASON: Facet<Item, State> = { sel: (s) => s.seasons, get: (i) => i.season, mode: 'any' }

const ITEM: Item = { rank: 3, keywords: ['Burst', 'Combustion'], season: 2 }

function state(overrides: Partial<State>): State {
  return { ...EMPTY_STATE, ...overrides }
}

describe('applyFacets', () => {
  it('passes an item when there are no facets', () => {
    expect(applyFacets(ITEM, EMPTY_STATE, [])).toBe(true)
  })

  describe('skipped selections', () => {
    const CASES: { name: string; sel: Facet<Item, State>['sel'] }[] = [
      { name: 'an empty selection passes', sel: (s) => s.ranks },
      { name: 'an undefined selection passes', sel: () => undefined },
    ]

    it.each(CASES)('$name', ({ sel }) => {
      const facet: Facet<Item, State> = { sel, get: () => 'never-selected', mode: 'any' }
      expect(applyFacets(ITEM, EMPTY_STATE, [facet])).toBe(true)
    })

    it('skips a mode-gated facet even when other facets reject', () => {
      const gated: Facet<Item, State> = { sel: () => undefined, get: () => 'x', mode: 'all' }
      expect(applyFacets(ITEM, state({ ranks: new Set([1]) }), [gated, RANK])).toBe(false)
    })
  })

  describe('mode any', () => {
    const CASES: { name: string; selected: number[]; expected: boolean }[] = [
      { name: 'matches the single value', selected: [3], expected: true },
      { name: 'matches one of several', selected: [1, 2, 3], expected: true },
      { name: 'rejects when no value is selected', selected: [1, 2], expected: false },
    ]

    it.each(CASES)('scalar $name', ({ selected, expected }) => {
      expect(applyFacets(ITEM, state({ ranks: new Set(selected) }), [RANK])).toBe(expected)
    })

    const ARRAY_CASES: { name: string; selected: string[]; expected: boolean }[] = [
      { name: 'matches on one carried value', selected: ['Burst'], expected: true },
      {
        name: 'matches when any selected value is carried',
        selected: ['Burst', 'Poise'],
        expected: true,
      },
      { name: 'rejects when none is carried', selected: ['Poise'], expected: false },
    ]

    it.each(ARRAY_CASES)('array $name', ({ selected, expected }) => {
      expect(applyFacets(ITEM, state({ keywordsAny: new Set(selected) }), [KEYWORDS_ANY])).toBe(
        expected,
      )
    })
  })

  describe('mode all', () => {
    const CASES: { name: string; selected: string[]; expected: boolean }[] = [
      { name: 'one selected value the item carries', selected: ['Burst'], expected: true },
      { name: 'every selected value carried', selected: ['Burst', 'Combustion'], expected: true },
      { name: 'one selected value missing', selected: ['Burst', 'Poise'], expected: false },
      { name: 'no selected value carried', selected: ['Poise'], expected: false },
    ]

    it.each(CASES)('$name', ({ selected, expected }) => {
      expect(applyFacets(ITEM, state({ keywordsAll: new Set(selected) }), [KEYWORDS_ALL])).toBe(
        expected,
      )
    })

    it('rejects an item carrying nothing', () => {
      const bare: Item = { rank: 1, keywords: [] }
      expect(applyFacets(bare, state({ keywordsAll: new Set(['Burst']) }), [KEYWORDS_ALL])).toBe(
        false,
      )
    })
  })

  describe('composition', () => {
    it('ANDs facets together', () => {
      const both = state({ ranks: new Set([3]), keywordsAll: new Set(['Burst']) })
      expect(applyFacets(ITEM, both, [RANK, KEYWORDS_ALL])).toBe(true)
    })

    it('rejects when only one facet rejects', () => {
      const mixed = state({ ranks: new Set([2]), keywordsAll: new Set(['Burst']) })
      expect(applyFacets(ITEM, mixed, [RANK, KEYWORDS_ALL])).toBe(false)
    })

    it('rejects an undefined read against a non-empty selection', () => {
      const seasonless: Item = { rank: 3, keywords: [] }
      expect(applyFacets(seasonless, state({ seasons: new Set([2]) }), [SEASON])).toBe(false)
    })
  })
})

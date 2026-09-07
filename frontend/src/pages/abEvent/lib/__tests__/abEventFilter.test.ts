import { describe, it, expect } from 'vitest'
import type { FilterState } from '@/components/hooks/useSetFilters'
import {
  buildAbEventSearchTerms,
  matchesAbEvent,
  type AbEventFacetState,
  type AbEventListItem,
} from '../abEventFilter'

const ITEM: AbEventListItem = [
  '901001',
  { relatedEgoGifts: ['9001'], relatedThemePacks: ['1002'], hasImage: true },
]

function state(
  searchQuery = '',
  values: Partial<AbEventFacetState> = {},
): FilterState<AbEventFacetState> {
  return {
    values: { selectedEgoGifts: new Set(), selectedThemePacks: new Set(), ...values },
    searchQuery,
  }
}

describe('buildAbEventSearchTerms', () => {
  it('lowercases the description', () => {
    expect(buildAbEventSearchTerms('901001', { '901001': 'A Cold Cement Room' })).toEqual([
      'a cold cement room',
    ])
  })

  it('yields an empty term for an id with no description', () => {
    expect(buildAbEventSearchTerms('901001', {})).toEqual([''])
  })
})

describe('matchesAbEvent', () => {
  const terms = buildAbEventSearchTerms('901001', { '901001': 'A cold cement room' })

  it('matches a query anywhere in the description, case-insensitively', () => {
    expect(matchesAbEvent(ITEM, state('CEMENT'), terms)).toBe(true)
  })

  it('rejects a query the description lacks', () => {
    expect(matchesAbEvent(ITEM, state('lantern'), terms)).toBe(false)
  })

  it('matches everything on an empty query', () => {
    expect(matchesAbEvent(ITEM, state(''), [''])).toBe(true)
  })

  it('ANDs the search with the facets', () => {
    const facets = { selectedEgoGifts: new Set(['9999']) }
    expect(matchesAbEvent(ITEM, state('cement', facets), terms)).toBe(false)
  })
})

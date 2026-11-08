import { describe, it, expect } from 'vitest'
import type { FilterState } from '@/components/hooks/filterStore'
import { buildAbEventSearchTerms, matchesAbEvent, type AbEventFacetState } from '../abEventFilter'
import { toAbEventEntity } from '../abEventEntity'
import type { AbEventEntity } from '../../types/AbEventTypes'
import type { AbEventResultType } from '@/shared/gameData'

const ITEM: AbEventEntity = toAbEventEntity('901001', {
  relatedEgoGifts: ['9001'],
  relatedThemePacks: ['1002'],
  resultTypes: [],
  hasImage: true,
})

function state(
  searchQuery = '',
  values: Partial<AbEventFacetState> = {},
): FilterState<AbEventFacetState> {
  return {
    values: {
      selectedEgoGifts: new Set(),
      selectedThemePacks: new Set(),
      selectedResultTypes: new Set(),
      ...values,
    },
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

describe('matchesAbEvent result types', () => {
  const levelUp = toAbEventEntity('901001', {
    relatedEgoGifts: [],
    relatedThemePacks: [],
    resultTypes: ['levelUp'],
    hasImage: true,
  })
  const hpDamage = toAbEventEntity('901002', {
    relatedEgoGifts: [],
    relatedThemePacks: [],
    resultTypes: ['hpDamage'],
    hasImage: true,
  })
  const noResult = toAbEventEntity('901003', {
    relatedEgoGifts: [],
    relatedThemePacks: [],
    resultTypes: [],
    hasImage: true,
  })
  const events = [levelUp, hpDamage, noResult]

  function survivors(selectedResultTypes: ReadonlySet<AbEventResultType>): string[] {
    return events
      .filter((event) => matchesAbEvent(event, state('', { selectedResultTypes }), ['']))
      .map((event) => event.id)
  }

  it('selects by one kind', () => {
    expect(survivors(new Set(['levelUp']))).toEqual(['901001'])
  })

  it('combines kinds as any', () => {
    expect(survivors(new Set(['levelUp', 'hpDamage']))).toEqual(['901001', '901002'])
  })

  it('selects every event when no kind is selected', () => {
    expect(survivors(new Set())).toEqual(['901001', '901002', '901003'])
  })

  it('ANDs the kind facet with the gift facet', () => {
    const giftBattle = toAbEventEntity('901004', {
      relatedEgoGifts: ['9001'],
      relatedThemePacks: [],
      resultTypes: ['battle'],
      hasImage: true,
    })
    const facets = {
      selectedEgoGifts: new Set(['9001']),
      selectedResultTypes: new Set<AbEventResultType>(['levelUp']),
    }
    expect(matchesAbEvent(giftBattle, state('', facets), [''])).toBe(false)
    expect(
      matchesAbEvent(giftBattle, state('', { selectedEgoGifts: facets.selectedEgoGifts }), ['']),
    ).toBe(true)
  })
})

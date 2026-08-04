/**
 * keywordFacets.parity.test.ts
 *
 * Pins KEYWORD_FACETS to the hand-written guard sequence KeywordList used
 * before the facet migration, over the full cartesian product of selections.
 */

import { describe, it, expect } from 'vitest'
import { applyFacets } from '@/shared/filter'
import type { BuffType } from '@/shared/gameData'
import { enumerateSelectionStates, findParityMismatches } from '@/test-utils/facetParity'
import { KEYWORD_FACETS, type KeywordFacetItem, type KeywordFacetState } from '../keywordFilter'

interface KeywordFixture extends KeywordFacetItem {
  id: string
}

function legacyMatches(keyword: KeywordFixture, state: KeywordFacetState): boolean {
  const { selectedBuffTypes, selectedIdentities, selectedEgos, selectedEgoGifts } = state

  if (selectedBuffTypes.size > 0 && !selectedBuffTypes.has(keyword.buffType as BuffType)) {
    return false
  }

  if (selectedIdentities.size > 0) {
    if (!keyword.identities.some((id) => selectedIdentities.has(id))) return false
  }

  if (selectedEgos.size > 0) {
    if (!keyword.egos.some((id) => selectedEgos.has(id))) return false
  }

  if (selectedEgoGifts.size > 0) {
    if (!keyword.egoGifts.some((id) => selectedEgoGifts.has(id))) return false
  }

  return true
}

const ITEMS: KeywordFixture[] = [
  {
    id: 'Combustion',
    buffType: 'Negative',
    identities: ['10101'],
    egos: ['20101'],
    egoGifts: ['9001'],
  },
  {
    id: 'Laceration',
    buffType: 'Positive',
    identities: ['10101', '10201'],
    egos: ['20101', '20201'],
    egoGifts: ['9001', '9002'],
  },
  { id: 'Tremor', buffType: 'Negative', identities: [], egos: [], egoGifts: [] },
  {
    id: 'Poise',
    buffType: 'Positive',
    identities: ['10201'],
    egos: ['20201'],
    egoGifts: ['9002'],
  },
  {
    id: 'Sinking',
    buffType: 'Neutral',
    identities: ['10301'],
    egos: [],
    egoGifts: ['9001', '9003'],
  },
]

const BASE_STATE: KeywordFacetState = {
  selectedBuffTypes: new Set(),
  selectedIdentities: new Set(),
  selectedEgos: new Set(),
  selectedEgoGifts: new Set(),
}

const STATES = enumerateSelectionStates(BASE_STATE, {
  selectedBuffTypes: [[], ['Positive'], ['Negative'], ['Positive', 'Negative']],
  selectedIdentities: [[], ['10101'], ['10201'], ['10101', '10201']],
  selectedEgos: [[], ['20101'], ['20201'], ['20101', '20201']],
  selectedEgoGifts: [[], ['9001'], ['9002'], ['9001', '9002']],
})

describe('KEYWORD_FACETS parity', () => {
  it('enumerates every selection combination', () => {
    expect(STATES.length).toBe(4 ** 4)
  })

  it('survives the same ids as the pre-migration guards', () => {
    const mismatches = findParityMismatches(
      ITEMS,
      STATES,
      (item, state) => legacyMatches(item, state),
      (item, state) => applyFacets(item, state, KEYWORD_FACETS),
    )
    expect(mismatches).toEqual([])
  })

  it('keeps entity facets on ANY, not ALL', () => {
    const bothIdentities: KeywordFacetState = {
      ...BASE_STATE,
      selectedIdentities: new Set(['10101', '10201']),
    }
    const survivors = ITEMS.filter((item) => applyFacets(item, bothIdentities, KEYWORD_FACETS))
    expect(survivors.map((item) => item.id)).toEqual(['Combustion', 'Laceration', 'Poise'])
  })
})

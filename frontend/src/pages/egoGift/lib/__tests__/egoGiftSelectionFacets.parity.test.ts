/**
 * egoGiftSelectionFacets.parity.test.ts
 *
 * Pins EGO_GIFT_SELECTION_FACETS to the keyword guard EGOGiftSelectionList used
 * before the facet migration, over the full cartesian product of selections.
 */

import { describe, it, expect } from 'vitest'
import { applyFacets } from '@/shared/filter'
import { enumerateSelectionStates, findParityMismatches } from '@/test-utils/facetParity'
import { EGO_GIFT_SELECTION_FACETS, type EGOGiftSelectionFacetState } from '../egoGiftFilter'
import type { EGOGiftListItem } from '../../types/EGOGiftTypes'
import { EGOGiftIdSchema } from '@/shared/gameData'

function legacyMatches(gift: EGOGiftListItem, state: EGOGiftSelectionFacetState): boolean {
  if (state.selectedKeywords.size > 0) {
    const giftKeyword = gift.keyword ?? 'None'
    if (!state.selectedKeywords.has(giftKeyword)) return false
  }
  return true
}

function makeGift(overrides: Partial<EGOGiftListItem> & { id: string }): EGOGiftListItem {
  return {
    name: 'Fixture',
    tag: [],
    keyword: null,
    battleKeywordList: [],
    attributeType: 'CRIMSON',
    themePack: [],
    maxEnhancement: 0,
    ...overrides,
  }
}

const ITEMS: EGOGiftListItem[] = [
  makeGift({ id: EGOGiftIdSchema.parse('9001'), keyword: 'Burn' }),
  makeGift({ id: EGOGiftIdSchema.parse('9002'), keyword: 'Bleed' }),
  makeGift({ id: EGOGiftIdSchema.parse('9003'), keyword: null }),
  makeGift({ id: EGOGiftIdSchema.parse('9004'), keyword: 'None' }),
  makeGift({ id: EGOGiftIdSchema.parse('9005'), keyword: undefined as unknown as string }),
]

const BASE_STATE: EGOGiftSelectionFacetState = { selectedKeywords: new Set() }

const STATES = enumerateSelectionStates(BASE_STATE, {
  selectedKeywords: [
    [],
    ['Burn'],
    ['Bleed'],
    ['None'],
    ['Burn', 'Bleed'],
    ['Burn', 'None'],
    ['Burn', 'Bleed', 'None'],
  ],
})

describe('EGO_GIFT_SELECTION_FACETS parity', () => {
  it('enumerates every selection combination', () => {
    expect(STATES.length).toBe(7)
  })

  it('survives the same ids as the pre-migration guard', () => {
    const mismatches = findParityMismatches(
      ITEMS,
      STATES,
      (item, state) => legacyMatches(item, state),
      (item, state) => applyFacets(item, state, EGO_GIFT_SELECTION_FACETS),
    )
    expect(mismatches).toEqual([])
  })

  it('folds a missing keyword into None, unlike the browser list', () => {
    const noneOnly: EGOGiftSelectionFacetState = { selectedKeywords: new Set(['None']) }
    const survivors = ITEMS.filter((item) => applyFacets(item, noneOnly, EGO_GIFT_SELECTION_FACETS))
    expect(survivors.map((item) => item.id)).toEqual(['9003', '9004', '9005'])
  })
})

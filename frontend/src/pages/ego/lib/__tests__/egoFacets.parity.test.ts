/**
 * egoFacets.parity.test.ts
 *
 * Pins EGO_FACETS to the hand-written guard sequence EGOList used before the
 * facet migration, over the full cartesian product of selections.
 */

import { describe, it, expect } from 'vitest'
import { applyFacets } from '@/shared/filter'
import { getSinnerFromId } from '@/shared/gameData'
import { enumerateSelectionStates, findParityMismatches } from '@/test-utils/facetParity'
import { EGO_FACETS, type EGOFacetState } from '../egoFilter'
import type { EGOListItem } from '../../types/EGOTypes'

function legacyMatches(ego: EGOListItem, state: EGOFacetState): boolean {
  const {
    selectedSinners,
    selectedKeywords,
    selectedBattleKeywords,
    selectedAttributes,
    selectedAtkTypes,
    selectedEGOTypes,
    selectedSeasons,
  } = state

  if (selectedSinners.size > 0) {
    if (!selectedSinners.has(getSinnerFromId(ego.id))) return false
  }

  if (selectedKeywords.size > 0) {
    const hasAllKeywords = Array.from(selectedKeywords).every((selectedKeyword) =>
      ego.skillKeywordList.includes(selectedKeyword),
    )
    if (!hasAllKeywords) return false
  }

  if (selectedBattleKeywords.size > 0) {
    const hasAnyBattleKeyword = (ego.battleKeywordList ?? []).some((keyword) =>
      selectedBattleKeywords.has(keyword),
    )
    if (!hasAnyBattleKeyword) return false
  }

  if (selectedAttributes.size > 0) {
    const hasAllAttributes = Array.from(selectedAttributes).every((attr) =>
      ego.attributeTypes.includes(attr),
    )
    if (!hasAllAttributes) return false
  }

  if (selectedAtkTypes.size > 0) {
    const hasAllAtkTypes = Array.from(selectedAtkTypes).every((atkType) =>
      ego.atkTypes.includes(atkType),
    )
    if (!hasAllAtkTypes) return false
  }

  if (selectedEGOTypes.size > 0) {
    if (!selectedEGOTypes.has(ego.egoType)) return false
  }

  if (selectedSeasons.size > 0) {
    if (!selectedSeasons.has(ego.season)) return false
  }

  return true
}

function makeEgo(overrides: Partial<EGOListItem> & { id: string }): EGOListItem {
  return {
    name: 'Fixture',
    egoType: 'ZAYIN',
    skillKeywordList: [],
    battleKeywordList: [],
    attributeTypes: [],
    atkTypes: [],
    updateDate: 20240101,
    season: 0,
    maxThreadspin: 4,
    ...overrides,
  }
}

const ITEMS: EGOListItem[] = [
  makeEgo({
    id: '20101',
    egoType: 'ZAYIN',
    skillKeywordList: ['Combustion', 'Laceration'],
    battleKeywordList: ['Poise'],
    attributeTypes: ['AZURE', 'VIOLET'],
    atkTypes: ['SLASH', 'PENETRATE'],
    season: 1,
  }),
  makeEgo({
    id: '20201',
    egoType: 'ALEPH',
    skillKeywordList: ['Combustion'],
    battleKeywordList: ['Poise', 'Sinking'],
    attributeTypes: ['AZURE'],
    atkTypes: ['SLASH'],
    season: 5,
  }),
  makeEgo({ id: '20301', egoType: 'TETH' }),
  makeEgo({
    id: '20401',
    egoType: 'ALEPH',
    battleKeywordList: undefined as unknown as string[],
    skillKeywordList: ['Laceration'],
    attributeTypes: ['VIOLET'],
    atkTypes: ['PENETRATE'],
    season: 1,
  }),
  makeEgo({
    id: '20501',
    egoType: 'ZAYIN',
    skillKeywordList: ['Combustion', 'Laceration', 'Tremor'],
    battleKeywordList: ['Sinking'],
    attributeTypes: ['AZURE', 'VIOLET', 'AMBER'],
    atkTypes: ['SLASH', 'PENETRATE', 'HIT'],
    season: 5,
  }),
  makeEgo({
    id: '21201',
    egoType: 'WAW',
    skillKeywordList: ['Tremor'],
    battleKeywordList: ['Poise'],
    attributeTypes: ['AMBER'],
    atkTypes: ['HIT'],
    season: 1,
  }),
]

const BASE_STATE: EGOFacetState = {
  selectedSinners: new Set(),
  selectedKeywords: new Set(),
  selectedBattleKeywords: new Set(),
  selectedAttributes: new Set(),
  selectedAtkTypes: new Set(),
  selectedEGOTypes: new Set(),
  selectedSeasons: new Set(),
}

const STATES = enumerateSelectionStates(BASE_STATE, {
  selectedSinners: [[], ['YiSang'], ['YiSang', 'Faust']],
  selectedKeywords: [[], ['Combustion'], ['Combustion', 'Laceration']],
  selectedBattleKeywords: [[], ['Poise'], ['Poise', 'Sinking']],
  selectedAttributes: [[], ['AZURE'], ['AZURE', 'VIOLET']],
  selectedAtkTypes: [[], ['SLASH'], ['SLASH', 'PENETRATE']],
  selectedEGOTypes: [[], ['ALEPH'], ['ALEPH', 'ZAYIN']],
  selectedSeasons: [[], [1], [1, 5]],
})

describe('EGO_FACETS parity', () => {
  it('enumerates every selection combination', () => {
    expect(STATES.length).toBe(3 ** 7)
  })

  it('survives the same ids as the pre-migration guards', () => {
    const mismatches = findParityMismatches(
      ITEMS,
      STATES,
      (item, state) => legacyMatches(item, state),
      (item, state) => applyFacets(item, state, EGO_FACETS),
    )
    expect(mismatches).toEqual([])
  })

  it('distinguishes ALL from ANY on multi-valued facets', () => {
    const allAtkTypes: EGOFacetState = {
      ...BASE_STATE,
      selectedAtkTypes: new Set(['SLASH', 'PENETRATE']),
    }
    const survivors = ITEMS.filter((item) => applyFacets(item, allAtkTypes, EGO_FACETS))
    expect(survivors.map((item) => item.id)).toEqual(['20101', '20501'])
  })
})

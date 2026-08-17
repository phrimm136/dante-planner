/**
 * identityFacets.parity.test.ts
 *
 * Pins IDENTITY_FACETS to the hand-written guard sequence IdentityList used
 * before the facet migration, over the full cartesian product of selections.
 */

import { describe, it, expect } from 'vitest'
import { applyFacets } from '@/shared/filter'
import { getSinnerFromId } from '@/shared/gameData'
import { enumerateSelectionStates, findParityMismatches } from '@/test-utils/facetParity'
import { IDENTITY_FACETS, type IdentityFacetState } from '../identityFilter'
import type { IdentityListItem } from '../../types/IdentityTypes'

function legacyMatches(identity: IdentityListItem, state: IdentityFacetState): boolean {
  const {
    selectedSinners,
    selectedKeywords,
    selectedBattleKeywords,
    selectedAttributes,
    selectedAtkTypes,
    selectedDefTypes,
    selectedRaritys,
    selectedSeasons,
    selectedUnitKeywords,
  } = state

  if (selectedSinners.size > 0) {
    if (!selectedSinners.has(getSinnerFromId(identity.id))) return false
  }

  if (selectedKeywords.size > 0) {
    const hasAllKeywords = Array.from(selectedKeywords).every((selectedKeyword) =>
      identity.skillKeywordList.includes(selectedKeyword),
    )
    if (!hasAllKeywords) return false
  }

  if (selectedBattleKeywords.size > 0) {
    const hasAnyBattleKeyword = (identity.battleKeywordList ?? []).some((keyword) =>
      selectedBattleKeywords.has(keyword),
    )
    if (!hasAnyBattleKeyword) return false
  }

  if (selectedAttributes.size > 0) {
    const hasAllAttributes = Array.from(selectedAttributes).every((attr) =>
      identity.attributeTypes.includes(attr),
    )
    if (!hasAllAttributes) return false
  }

  if (selectedAtkTypes.size > 0) {
    const hasAllAtkTypes = Array.from(selectedAtkTypes).every((atkType) =>
      identity.atkTypes.includes(atkType),
    )
    if (!hasAllAtkTypes) return false
  }

  if (selectedDefTypes.size > 0) {
    const hasAllDefTypes = Array.from(selectedDefTypes).every((defType) =>
      identity.defenseTypes.includes(defType),
    )
    if (!hasAllDefTypes) return false
  }

  if (selectedRaritys.size > 0) {
    if (!selectedRaritys.has(identity.rank)) return false
  }

  if (selectedSeasons.size > 0) {
    if (!selectedSeasons.has(identity.season)) return false
  }

  if (selectedUnitKeywords.size > 0) {
    const hasAnyUnitKeyword = identity.unitKeywordList.some((keyword) =>
      selectedUnitKeywords.has(keyword),
    )
    if (!hasAnyUnitKeyword) return false
  }

  return true
}

function makeIdentity(overrides: Partial<IdentityListItem> & { id: string }): IdentityListItem {
  return {
    name: 'Fixture',
    rank: 0,
    updateDate: 20240101,
    unitKeywordList: [],
    skillKeywordList: [],
    battleKeywordList: [],
    attributeTypes: [],
    atkTypes: [],
    defenseTypes: [],
    season: 0,
    ...overrides,
  }
}

const ITEMS: IdentityListItem[] = [
  makeIdentity({
    id: '10101',
    skillKeywordList: ['Combustion', 'Laceration'],
    battleKeywordList: ['Poise'],
    attributeTypes: ['AZURE', 'VIOLET'],
    atkTypes: ['SLASH', 'PENETRATE'],
    defenseTypes: ['GUARD', 'EVADE'],
    rank: 0,
    season: 1,
    unitKeywordList: ['BLADE_LINEAGE'],
  }),
  makeIdentity({
    id: '10201',
    skillKeywordList: ['Combustion'],
    battleKeywordList: ['Poise', 'Sinking'],
    attributeTypes: ['AZURE'],
    atkTypes: ['SLASH'],
    defenseTypes: ['GUARD'],
    rank: 2,
    season: 5,
    unitKeywordList: ['BLADE_LINEAGE', 'KURO_NAMI'],
  }),
  makeIdentity({ id: '10301', rank: 3, season: 0 }),
  makeIdentity({
    id: '10401',
    battleKeywordList: undefined as unknown as string[],
    skillKeywordList: ['Laceration'],
    attributeTypes: ['VIOLET'],
    atkTypes: ['PENETRATE'],
    defenseTypes: ['EVADE'],
    rank: 0,
    season: 1,
    unitKeywordList: ['KURO_NAMI'],
  }),
  makeIdentity({
    id: '10501',
    skillKeywordList: ['Combustion', 'Laceration', 'Tremor'],
    battleKeywordList: ['Sinking'],
    attributeTypes: ['AZURE', 'VIOLET', 'AMBER'],
    atkTypes: ['SLASH', 'PENETRATE', 'HIT'],
    defenseTypes: ['GUARD', 'EVADE', 'COUNTER'],
    rank: 2,
    season: 1,
    unitKeywordList: ['BLADE_LINEAGE', 'KURO_NAMI'],
  }),
  makeIdentity({
    id: '10601',
    skillKeywordList: ['Tremor'],
    battleKeywordList: ['Poise'],
    attributeTypes: ['AMBER'],
    atkTypes: ['HIT'],
    defenseTypes: ['COUNTER'],
    rank: 3,
    season: 5,
    unitKeywordList: ['KURO_NAMI'],
  }),
  makeIdentity({
    id: '11201',
    skillKeywordList: ['Laceration', 'Combustion'],
    battleKeywordList: ['Sinking', 'Poise'],
    attributeTypes: ['VIOLET', 'AZURE'],
    atkTypes: ['PENETRATE', 'SLASH'],
    defenseTypes: ['EVADE', 'GUARD'],
    rank: 0,
    season: 5,
    unitKeywordList: ['BLADE_LINEAGE'],
  }),
  makeIdentity({ id: '10901', rank: 2, season: 1, unitKeywordList: ['BLADE_LINEAGE'] }),
]

const BASE_STATE: IdentityFacetState = {
  selectedSinners: new Set(),
  selectedKeywords: new Set(),
  selectedBattleKeywords: new Set(),
  selectedAttributes: new Set(),
  selectedAtkTypes: new Set(),
  selectedDefTypes: new Set(),
  selectedRaritys: new Set(),
  selectedSeasons: new Set(),
  selectedUnitKeywords: new Set(),
}

const STATES = enumerateSelectionStates(BASE_STATE, {
  selectedSinners: [[], ['YiSang'], ['YiSang', 'Faust']],
  selectedKeywords: [[], ['Combustion'], ['Combustion', 'Laceration']],
  selectedBattleKeywords: [[], ['Poise'], ['Poise', 'Sinking']],
  selectedAttributes: [[], ['AZURE'], ['AZURE', 'VIOLET']],
  selectedAtkTypes: [[], ['SLASH'], ['SLASH', 'PENETRATE']],
  selectedDefTypes: [[], ['GUARD'], ['GUARD', 'EVADE']],
  selectedRaritys: [[], [0], [0, 2]],
  selectedSeasons: [[], [1], [1, 5]],
  selectedUnitKeywords: [[], ['BLADE_LINEAGE'], ['BLADE_LINEAGE', 'KURO_NAMI']],
})

describe('IDENTITY_FACETS parity', () => {
  it('enumerates every selection combination', () => {
    expect(STATES.length).toBe(3 ** 9)
  })

  it('survives the same ids as the pre-migration guards', () => {
    const mismatches = findParityMismatches(
      ITEMS,
      STATES,
      (item, state) => legacyMatches(item, state),
      (item, state) => applyFacets(item, state, IDENTITY_FACETS),
    )
    expect(mismatches).toEqual([])
  })

  it('distinguishes ALL from ANY on multi-valued facets', () => {
    const allAttributes: IdentityFacetState = {
      ...BASE_STATE,
      selectedAttributes: new Set(['AZURE', 'VIOLET']),
    }
    const survivors = ITEMS.filter((item) => applyFacets(item, allAttributes, IDENTITY_FACETS))
    expect(survivors.map((item) => item.id)).toEqual(['10101', '10501', '11201'])
  })
})

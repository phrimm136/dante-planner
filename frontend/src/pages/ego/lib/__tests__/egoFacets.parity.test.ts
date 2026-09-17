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
import { asEGOId } from '@/test-utils/fixtures'
import { EGO_FACETS, type EGOFacetState } from '../egoFilter'
import type { EGOEntity } from '../../types/EGOTypes'

function legacyMatches(ego: EGOEntity, state: EGOFacetState): boolean {
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
    const hasAnyBattleKeyword = ego.battleKeywordList.some((keyword) =>
      selectedBattleKeywords.has(keyword),
    )
    if (!hasAnyBattleKeyword) return false
  }

  if (selectedAttributes.size > 0) {
    const hasAllAttributes = Array.from(selectedAttributes).every((attr) =>
      ego.attributeType.some((value) => value === attr),
    )
    if (!hasAllAttributes) return false
  }

  if (selectedAtkTypes.size > 0) {
    const hasAllAtkTypes = Array.from(selectedAtkTypes).every((atkType) =>
      ego.atkType.includes(atkType),
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

function makeEgo(overrides: Partial<EGOEntity> & { id: string }): EGOEntity {
  return {
    name: 'Fixture',
    egoType: 'ZAYIN',
    skillKeywordList: [],
    battleKeywordList: [],
    requirements: {},
    attributeType: [],
    atkType: [],
    updateDate: 20240101,
    season: 0,
    maxThreadspin: 4,
    ...overrides,
  }
}

const EGO_20101 = asEGOId('20101')
const EGO_20201 = asEGOId('20201')
const EGO_20301 = asEGOId('20301')
const EGO_20401 = asEGOId('20401')
const EGO_20501 = asEGOId('20501')
const EGO_21201 = asEGOId('21201')

const ITEMS: EGOEntity[] = [
  makeEgo({
    id: EGO_20101,
    egoType: 'ZAYIN',
    skillKeywordList: ['Combustion', 'Laceration'],
    battleKeywordList: ['Poise'],
    attributeType: ['AZURE', 'VIOLET'],
    atkType: ['SLASH', 'PENETRATE'],
    season: 1,
  }),
  makeEgo({
    id: EGO_20201,
    egoType: 'ALEPH',
    skillKeywordList: ['Combustion'],
    battleKeywordList: ['Poise', 'Sinking'],
    attributeType: ['AZURE'],
    atkType: ['SLASH'],
    season: 5,
  }),
  makeEgo({ id: EGO_20301, egoType: 'TETH' }),
  makeEgo({
    id: EGO_20401,
    egoType: 'ALEPH',
    battleKeywordList: [],
    skillKeywordList: ['Laceration'],
    attributeType: ['VIOLET'],
    atkType: ['PENETRATE'],
    season: 1,
  }),
  makeEgo({
    id: EGO_20501,
    egoType: 'ZAYIN',
    skillKeywordList: ['Combustion', 'Laceration', 'Tremor'],
    battleKeywordList: ['Sinking'],
    attributeType: ['AZURE', 'VIOLET', 'AMBER'],
    atkType: ['SLASH', 'PENETRATE', 'HIT'],
    season: 5,
  }),
  makeEgo({
    id: EGO_21201,
    egoType: 'WAW',
    skillKeywordList: ['Tremor'],
    battleKeywordList: ['Poise'],
    attributeType: ['AMBER'],
    atkType: ['HIT'],
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

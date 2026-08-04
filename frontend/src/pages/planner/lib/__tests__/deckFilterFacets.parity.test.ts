/**
 * deckFilterFacets.parity.test.ts
 *
 * Pins matchesDeckFilter to its pre-migration guard sequence, per entity mode,
 * over a capped cartesian product of selections.
 */

import { describe, it, expect } from 'vitest'
import { matchesDeckFilter } from '../deckFilter'
import type { DeckFilterState, EntityMode } from '../../types/DeckTypes'
import type { SearchMappings } from '@/shared/filter'
import type { Keyword } from '@/shared/gameData'
import { getSinnerFromId } from '@/shared/gameData'
import { enumerateSelectionStates, findParityMismatches } from '@/test-utils/facetParity'

// Taken from the predicate itself: tsconfig.app.json excludes tests, so the
// type-aware lint pass cannot resolve a barrel type import from here.
type DeckFilterItem = Parameters<typeof matchesDeckFilter>[0]
type IdentityOverrides = Omit<Partial<DeckFilterItem>, 'id'> & { id: string }
type EgoOverrides = IdentityOverrides

const EMPTY_MAPPINGS: SearchMappings = {
  keywordToValue: new Map(),
  unitKeywordToValue: new Map(),
}

function legacyMatches(
  item: DeckFilterItem,
  state: DeckFilterState,
  mode: EntityMode,
  searchMappings: SearchMappings,
): boolean {
  if (state.selectedSinners.size > 0) {
    if (!state.selectedSinners.has(getSinnerFromId(item.id))) return false
  }

  if (state.selectedKeywords.size > 0) {
    const keywords = Array.from(state.selectedKeywords)
    const hasAllKeywords = keywords.every((kw) => item.skillKeywordList.includes(kw as Keyword))
    if (!hasAllKeywords) return false
  }

  if (state.selectedAttributes.size > 0) {
    const hasAny = item.attributeTypes.some((attr) => state.selectedAttributes.has(attr))
    if (!hasAny) return false
  }

  if (state.selectedAtkTypes.size > 0) {
    const hasAny = item.atkTypes.some((atk) => state.selectedAtkTypes.has(atk))
    if (!hasAny) return false
  }

  if (state.selectedSeasons.size > 0) {
    if (!state.selectedSeasons.has(item.season)) return false
  }

  if (state.selectedBattleKeywords.size > 0) {
    const hasAny = (item.battleKeywordList ?? []).some((kw) => state.selectedBattleKeywords.has(kw))
    if (!hasAny) return false
  }

  if (mode === 'identity') {
    const identity = item as IdentityListItem

    if (state.selectedDefTypes.size > 0) {
      const hasAny = identity.defenseTypes.some((def) => state.selectedDefTypes.has(def))
      if (!hasAny) return false
    }

    if (state.selectedRaritys.size > 0) {
      if (!state.selectedRaritys.has(identity.rank)) return false
    }

    if (state.selectedUnitKeywords.size > 0) {
      const hasAny = identity.unitKeywordList.some((kw) => state.selectedUnitKeywords.has(kw))
      if (!hasAny) return false
    }
  } else {
    const ego = item as EGOListItem

    if (state.selectedEgoTypes.size > 0) {
      if (!state.selectedEgoTypes.has(ego.egoType)) return false
    }
  }

  if (state.searchQuery) {
    const lowerQuery = state.searchQuery.toLowerCase()
    const nameMatch = item.name?.toLowerCase().includes(lowerQuery) ?? false

    const keywordMatch = Array.from(searchMappings.keywordToValue.entries()).some(
      ([naturalLang, internalCodes]) => {
        if (!naturalLang.includes(lowerQuery)) return false
        return internalCodes.some((code) => item.skillKeywordList.includes(code as Keyword))
      },
    )

    let unitKeywordMatch = false
    if (mode === 'identity') {
      const identity = item as IdentityListItem
      unitKeywordMatch = Array.from(searchMappings.unitKeywordToValue.entries()).some(
        ([naturalLang, internalCodes]) => {
          if (!naturalLang.includes(lowerQuery)) return false
          return internalCodes.some((code) => identity.unitKeywordList.includes(code))
        },
      )
    }

    if (!nameMatch && !keywordMatch && !unitKeywordMatch) return false
  }

  return true
}

function makeIdentity(overrides: IdentityOverrides) {
  return {
    name: 'Fixture Identity',
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

function makeEgo(overrides: EgoOverrides) {
  return {
    name: 'Fixture EGO',
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

const IDENTITIES: IdentityListItem[] = [
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
    id: '11201',
    skillKeywordList: ['Combustion', 'Laceration', 'Tremor'],
    battleKeywordList: ['Sinking'],
    attributeTypes: ['AMBER'],
    atkTypes: ['HIT'],
    defenseTypes: ['COUNTER'],
    rank: 2,
    season: 5,
    unitKeywordList: [],
  }),
]

const EGOS: EGOListItem[] = [
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
    battleKeywordList: undefined as unknown as string[],
    attributeTypes: ['AZURE'],
    atkTypes: ['SLASH'],
    season: 5,
  }),
  makeEgo({ id: '20301', egoType: 'TETH' }),
  makeEgo({
    id: '21201',
    egoType: 'ALEPH',
    skillKeywordList: ['Tremor'],
    battleKeywordList: ['Sinking'],
    attributeTypes: ['AMBER'],
    atkTypes: ['HIT'],
    season: 1,
  }),
]

const BASE_STATE: DeckFilterState = {
  entityMode: 'identity',
  selectedSinners: new Set(),
  selectedKeywords: new Set(),
  selectedAttributes: new Set(),
  selectedAtkTypes: new Set(),
  selectedDefTypes: new Set(),
  selectedRaritys: new Set(),
  selectedEgoTypes: new Set(),
  selectedSeasons: new Set(),
  selectedUnitKeywords: new Set(),
  selectedBattleKeywords: new Set(),
  searchQuery: '',
}

const STATES = enumerateSelectionStates(BASE_STATE, {
  selectedSinners: [[], ['YiSang'], ['YiSang', 'Faust']],
  selectedKeywords: [[], ['Combustion'], ['Combustion', 'Laceration']],
  selectedAttributes: [[], ['AZURE'], ['AZURE', 'AMBER']],
  selectedAtkTypes: [[], ['SLASH'], ['SLASH', 'HIT']],
  selectedDefTypes: [[], ['GUARD'], ['GUARD', 'COUNTER']],
  selectedRaritys: [[], [0], [0, 2]],
  selectedEgoTypes: [[], ['ALEPH'], ['ALEPH', 'ZAYIN']],
  selectedSeasons: [[], [1], [1, 5]],
  selectedUnitKeywords: [[], ['BLADE_LINEAGE'], ['BLADE_LINEAGE', 'KURO_NAMI']],
  selectedBattleKeywords: [[], ['Poise'], ['Poise', 'Sinking']],
})

const SEARCH_STATES: DeckFilterState[] = STATES.slice(0, 50).flatMap((state) => [
  { ...state, searchQuery: 'fixture' },
  { ...state, searchQuery: 'identity' },
  { ...state, searchQuery: 'nonexistent' },
])

describe('matchesDeckFilter facet parity', () => {
  it('enumerates a broad selection matrix', () => {
    expect(STATES.length).toBeGreaterThan(10000)
  })

  it('survives the same identities in identity mode', () => {
    const mismatches = findParityMismatches(
      IDENTITIES,
      STATES,
      (item, state) => legacyMatches(item, state, 'identity', EMPTY_MAPPINGS),
      (item, state) => matchesDeckFilter(item, state, 'identity', EMPTY_MAPPINGS),
    )
    expect(mismatches).toEqual([])
  })

  it('survives the same EGOs in EGO mode', () => {
    const mismatches = findParityMismatches(
      EGOS,
      STATES,
      (item, state) => legacyMatches(item, state, 'ego', EMPTY_MAPPINGS),
      (item, state) => matchesDeckFilter(item, state, 'ego', EMPTY_MAPPINGS),
    )
    expect(mismatches).toEqual([])
  })

  it('keeps search behaviour identical alongside facets', () => {
    const identityMismatches = findParityMismatches(
      IDENTITIES,
      SEARCH_STATES,
      (item, state) => legacyMatches(item, state, 'identity', EMPTY_MAPPINGS),
      (item, state) => matchesDeckFilter(item, state, 'identity', EMPTY_MAPPINGS),
    )
    const egoMismatches = findParityMismatches(
      EGOS,
      SEARCH_STATES,
      (item, state) => legacyMatches(item, state, 'ego', EMPTY_MAPPINGS),
      (item, state) => matchesDeckFilter(item, state, 'ego', EMPTY_MAPPINGS),
    )
    expect([...identityMismatches, ...egoMismatches]).toEqual([])
  })

  it('keeps attributes on ANY, unlike the identity browser', () => {
    const twoAttributes: DeckFilterState = {
      ...BASE_STATE,
      selectedAttributes: new Set(['AZURE', 'AMBER']),
    }
    const survivors = IDENTITIES.filter((item) =>
      matchesDeckFilter(item, twoAttributes, 'identity', EMPTY_MAPPINGS),
    )
    expect(survivors.map((item) => item.id)).toEqual(['10101', '10201', '11201'])
  })

  it('ignores identity-only facets in EGO mode', () => {
    const identityOnly: DeckFilterState = {
      ...BASE_STATE,
      selectedDefTypes: new Set(['GUARD']),
      selectedRaritys: new Set([3]),
      selectedUnitKeywords: new Set(['BLADE_LINEAGE']),
    }
    const survivors = EGOS.filter((item) =>
      matchesDeckFilter(item, identityOnly, 'ego', EMPTY_MAPPINGS),
    )
    expect(survivors.map((item) => item.id)).toEqual(EGOS.map((item) => item.id))
  })
})

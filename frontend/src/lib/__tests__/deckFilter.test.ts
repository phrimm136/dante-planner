/**
 * deckFilter.test.ts
 *
 * Unit tests for matchesDeckFilter predicate.
 * Covers mode gating, AND/OR semantics per category, search behavior,
 * and empty-set short-circuiting.
 */

import { describe, it, expect } from 'vitest'
import { matchesDeckFilter } from '../deckFilter'
import type { DeckFilterState, EntityMode } from '@/types/DeckTypes'
import type { IdentityListItem } from '@/types/IdentityTypes'
import type { EGOListItem } from '@/types/EGOTypes'
import type { SearchMappings } from '@/hooks/useSearchMappings'

const EMPTY_MAPPINGS: SearchMappings = {
  keywordToValue: new Map(),
  unitKeywordToValue: new Map(),
}

function makeState(overrides: Partial<DeckFilterState> = {}): DeckFilterState {
  return {
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
    ...overrides,
  }
}

// id 10101 -> sinner index 0 -> YiSang
function makeIdentity(overrides: Partial<IdentityListItem> = {}): IdentityListItem {
  return {
    id: '10101',
    name: 'LCB Sinner Yi Sang',
    rank: 0,
    updateDate: 20240101,
    unitKeywordList: ['BLADE_LINEAGE'],
    skillKeywordList: ['Combustion', 'Laceration'],
    battleKeywordList: ['Poise'],
    attributeTypes: ['AZURE', 'VIOLET'],
    atkTypes: ['SLASH', 'PENETRATE'],
    defenseTypes: ['GUARD'],
    season: 1,
    ...overrides,
  }
}

// id 20301 -> sinner index 2 -> DonQuixote
function makeEgo(overrides: Partial<EGOListItem> = {}): EGOListItem {
  return {
    id: '20301',
    name: 'Dimension Shredder',
    egoType: 'ZAYIN',
    skillKeywordList: ['Combustion'],
    battleKeywordList: [],
    attributeTypes: ['CRIMSON'],
    atkTypes: ['SLASH'],
    updateDate: 20240101,
    season: 1,
    ...overrides,
  }
}

describe('matchesDeckFilter — empty sets match all', () => {
  it('returns true for identity with all-empty filter state', () => {
    expect(matchesDeckFilter(makeIdentity(), makeState(), 'identity', EMPTY_MAPPINGS)).toBe(true)
  })

  it('returns true for EGO with all-empty filter state', () => {
    expect(matchesDeckFilter(makeEgo(), makeState({ entityMode: 'ego' }), 'ego', EMPTY_MAPPINGS)).toBe(true)
  })
})

describe('matchesDeckFilter — mode gating (id-only fields ignored in EGO mode)', () => {
  it('ignores selectedDefTypes in EGO mode', () => {
    const state = makeState({
      entityMode: 'ego',
      selectedDefTypes: new Set(['GUARD']),
    })
    expect(matchesDeckFilter(makeEgo(), state, 'ego', EMPTY_MAPPINGS)).toBe(true)
  })

  it('ignores selectedRaritys in EGO mode', () => {
    const state = makeState({
      entityMode: 'ego',
      selectedRaritys: new Set([2, 3]),
    })
    expect(matchesDeckFilter(makeEgo(), state, 'ego', EMPTY_MAPPINGS)).toBe(true)
  })

  it('ignores selectedUnitKeywords in EGO mode', () => {
    const state = makeState({
      entityMode: 'ego',
      selectedUnitKeywords: new Set(['BLADE_LINEAGE']),
    })
    expect(matchesDeckFilter(makeEgo(), state, 'ego', EMPTY_MAPPINGS)).toBe(true)
  })
})

describe('matchesDeckFilter — mode gating (ego-only field ignored in identity mode)', () => {
  it('ignores selectedEgoTypes in identity mode', () => {
    const state = makeState({
      selectedEgoTypes: new Set(['ALEPH']),
    })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(true)
  })
})

describe('matchesDeckFilter — sinner filter', () => {
  it('matches when item sinner is in selected set', () => {
    const state = makeState({ selectedSinners: new Set(['YiSang']) })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(true)
  })

  it('fails when item sinner is not in selected set', () => {
    const state = makeState({ selectedSinners: new Set(['Faust']) })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(false)
  })
})

describe('matchesDeckFilter — skill keywords (AND / all match)', () => {
  it('matches when item has all selected keywords', () => {
    const state = makeState({ selectedKeywords: new Set(['Combustion', 'Laceration']) })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(true)
  })

  it('fails when item has only some of the selected keywords', () => {
    const state = makeState({ selectedKeywords: new Set(['Combustion', 'Tremor']) })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(false)
  })

  it('fails when item has none of the selected keywords', () => {
    const state = makeState({ selectedKeywords: new Set(['Tremor']) })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(false)
  })
})

describe('matchesDeckFilter — skill attributes (ANY / OR)', () => {
  it('matches when item has any selected attribute', () => {
    const state = makeState({ selectedAttributes: new Set(['AZURE']) })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(true)
  })

  it('fails when item has none of the selected attributes', () => {
    const state = makeState({ selectedAttributes: new Set(['CRIMSON']) })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(false)
  })
})

describe('matchesDeckFilter — atk types (ANY / OR)', () => {
  it('matches when item has any selected atk type', () => {
    const state = makeState({ selectedAtkTypes: new Set(['SLASH']) })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(true)
  })

  it('fails when item has none of the selected atk types', () => {
    const state = makeState({ selectedAtkTypes: new Set(['HIT']) })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(false)
  })
})

describe('matchesDeckFilter — defense types (identity-only, ANY / OR)', () => {
  it('matches in identity mode when item has selected def type', () => {
    const state = makeState({ selectedDefTypes: new Set(['GUARD']) })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(true)
  })

  it('fails in identity mode when item lacks selected def type', () => {
    const state = makeState({ selectedDefTypes: new Set(['EVADE']) })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(false)
  })
})

describe('matchesDeckFilter — rank (identity-only)', () => {
  it('matches when item rank is in selected set', () => {
    const state = makeState({ selectedRaritys: new Set([0, 1]) })
    expect(matchesDeckFilter(makeIdentity({ rank: 0 }), state, 'identity', EMPTY_MAPPINGS)).toBe(true)
  })

  it('fails when item rank is not in selected set', () => {
    const state = makeState({ selectedRaritys: new Set([2, 3]) })
    expect(matchesDeckFilter(makeIdentity({ rank: 0 }), state, 'identity', EMPTY_MAPPINGS)).toBe(false)
  })
})

describe('matchesDeckFilter — EGO type (ego-only)', () => {
  it('matches ALEPH EGO when ALEPH selected', () => {
    const state = makeState({
      entityMode: 'ego',
      selectedEgoTypes: new Set(['ALEPH']),
    })
    expect(matchesDeckFilter(makeEgo({ egoType: 'ALEPH' }), state, 'ego', EMPTY_MAPPINGS)).toBe(true)
  })

  it('does not match ZAYIN EGO when only ALEPH selected', () => {
    const state = makeState({
      entityMode: 'ego',
      selectedEgoTypes: new Set(['ALEPH']),
    })
    expect(matchesDeckFilter(makeEgo({ egoType: 'ZAYIN' }), state, 'ego', EMPTY_MAPPINGS)).toBe(false)
  })
})

describe('matchesDeckFilter — season', () => {
  it('matches identity when season in set', () => {
    const state = makeState({ selectedSeasons: new Set([1]) })
    expect(matchesDeckFilter(makeIdentity({ season: 1 }), state, 'identity', EMPTY_MAPPINGS)).toBe(true)
  })

  it('fails identity when season not in set', () => {
    const state = makeState({ selectedSeasons: new Set([5]) })
    expect(matchesDeckFilter(makeIdentity({ season: 1 }), state, 'identity', EMPTY_MAPPINGS)).toBe(false)
  })

  it('applies to EGO mode as well', () => {
    const state = makeState({
      entityMode: 'ego',
      selectedSeasons: new Set([5]),
    })
    expect(matchesDeckFilter(makeEgo({ season: 5 }), state, 'ego', EMPTY_MAPPINGS)).toBe(true)
    expect(matchesDeckFilter(makeEgo({ season: 1 }), state, 'ego', EMPTY_MAPPINGS)).toBe(false)
  })
})

describe('matchesDeckFilter — unit keywords (identity-only, ANY / OR)', () => {
  it('matches when identity has any selected unit keyword', () => {
    const state = makeState({ selectedUnitKeywords: new Set(['BLADE_LINEAGE', 'KURO_NAMI']) })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(true)
  })

  it('fails when identity has none of the selected unit keywords', () => {
    const state = makeState({ selectedUnitKeywords: new Set(['KURO_NAMI']) })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(false)
  })
})

describe('matchesDeckFilter — battle keywords (ANY / OR)', () => {
  it('matches when item has any selected battle keyword', () => {
    const state = makeState({ selectedBattleKeywords: new Set(['Poise', 'Sinking']) })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(true)
  })

  it('fails when item has none of the selected battle keywords', () => {
    const state = makeState({ selectedBattleKeywords: new Set(['Sinking']) })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(false)
  })
})

describe('matchesDeckFilter — AND across categories', () => {
  it('requires every non-empty filter to match', () => {
    const state = makeState({
      selectedSinners: new Set(['YiSang']),
      selectedKeywords: new Set(['Combustion']),
      selectedDefTypes: new Set(['GUARD']),
      selectedSeasons: new Set([1]),
    })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(true)
  })

  it('fails if any single category fails', () => {
    const state = makeState({
      selectedSinners: new Set(['YiSang']),
      selectedKeywords: new Set(['Combustion']),
      selectedDefTypes: new Set(['EVADE']),
      selectedSeasons: new Set([1]),
    })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(false)
  })
})

describe('matchesDeckFilter — search', () => {
  it('matches by name substring (case-insensitive)', () => {
    const state = makeState({ searchQuery: 'yi sang' })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(true)
  })

  it('fails when name and mappings do not contain query', () => {
    const state = makeState({ searchQuery: 'nonexistent' })
    expect(matchesDeckFilter(makeIdentity(), state, 'identity', EMPTY_MAPPINGS)).toBe(false)
  })

  it('matches by skill-keyword display name via mappings', () => {
    const state = makeState({ searchQuery: 'burn' })
    const mappings: SearchMappings = {
      keywordToValue: new Map([['burn', ['Combustion']]]),
      unitKeywordToValue: new Map(),
    }
    expect(matchesDeckFilter(makeIdentity({ name: 'Other' }), state, 'identity', mappings)).toBe(true)
  })

  it('matches by unit-keyword display name in identity mode', () => {
    const state = makeState({ searchQuery: 'blade lineage' })
    const mappings: SearchMappings = {
      keywordToValue: new Map(),
      unitKeywordToValue: new Map([['blade lineage', ['BLADE_LINEAGE']]]),
    }
    expect(matchesDeckFilter(makeIdentity({ name: 'Other' }), state, 'identity', mappings)).toBe(true)
  })

  it('does not consult unit-keyword mappings in EGO mode', () => {
    const state = makeState({
      entityMode: 'ego',
      searchQuery: 'blade lineage',
    })
    const mappings: SearchMappings = {
      keywordToValue: new Map(),
      unitKeywordToValue: new Map([['blade lineage', ['BLADE_LINEAGE']]]),
    }
    expect(matchesDeckFilter(makeEgo({ name: 'Other' }), state, 'ego', mappings)).toBe(false)
  })

  it('matches EGO by skill-keyword mapping', () => {
    const state = makeState({
      entityMode: 'ego',
      searchQuery: 'burn',
    })
    const mappings: SearchMappings = {
      keywordToValue: new Map([['burn', ['Combustion']]]),
      unitKeywordToValue: new Map(),
    }
    expect(matchesDeckFilter(makeEgo({ name: 'Other' }), state, 'ego', mappings)).toBe(true)
  })
})

describe('matchesDeckFilter — type signature exercise', () => {
  it('accepts the union at the call site and narrows via mode', () => {
    const item: IdentityListItem | EGOListItem = makeIdentity()
    const mode: EntityMode = 'identity'
    const state = makeState({ selectedDefTypes: new Set(['GUARD']) })
    expect(matchesDeckFilter(item, state, mode, EMPTY_MAPPINGS)).toBe(true)
  })
})

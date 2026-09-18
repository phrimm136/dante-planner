import type { DeckFilterState, EntityMode } from '../types/DeckTypes'
import type { IdentityEntity } from '@/pages/identity'
import type { EGOEntity } from '@/pages/ego'
import type { Facet, SearchMappings } from '@/shared/filter'
import { applyFacets } from '@/shared/filter'
import type { Keyword } from '@/shared/gameData'
import { getSinnerFromId } from '@/shared/gameData'

type DeckFilterItem = IdentityEntity | EGOEntity

const DECK_FACETS: readonly Facet<DeckFilterItem, DeckFilterState>[] = [
  { sel: (s) => s.selectedSinners, get: (i) => getSinnerFromId(i.id), mode: 'any' },
  { sel: (s) => s.selectedKeywords, get: (i) => i.skillKeywordList, mode: 'all' },
  { sel: (s) => s.selectedAttributes, get: (i) => i.attributeType, mode: 'any' },
  { sel: (s) => s.selectedAtkTypes, get: (i) => i.atkType, mode: 'any' },
  { sel: (s) => s.selectedSeasons, get: (i) => i.season, mode: 'any' },
  { sel: (s) => s.selectedBattleKeywords, get: (i) => i.battleKeywordList, mode: 'any' },
  {
    sel: (s) => (s.entityMode === 'identity' ? s.selectedDefTypes : undefined),
    get: (i) => (i as IdentityEntity).defenseType,
    mode: 'any',
  },
  {
    sel: (s) => (s.entityMode === 'identity' ? s.selectedRaritys : undefined),
    get: (i) => (i as IdentityEntity).rank,
    mode: 'any',
  },
  {
    sel: (s) => (s.entityMode === 'identity' ? s.selectedUnitKeywords : undefined),
    get: (i) => (i as IdentityEntity).unitKeywordList,
    mode: 'any',
  },
  {
    sel: (s) => (s.entityMode === 'ego' ? s.selectedEgoTypes : undefined),
    get: (i) => (i as EGOEntity).egoType,
    mode: 'any',
  },
]

export function matchesDeckFilter(
  item: DeckFilterItem,
  state: DeckFilterState,
  mode: EntityMode,
  searchMappings: SearchMappings,
): boolean {
  if (!applyFacets(item, { ...state, entityMode: mode }, DECK_FACETS)) return false

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
      const identity = item as IdentityEntity
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

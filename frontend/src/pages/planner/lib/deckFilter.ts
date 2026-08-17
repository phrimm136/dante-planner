/**
 * Deck Builder Filter Predicate
 *
 * Pure function that evaluates whether an identity or EGO matches the
 * current deck filter state. Mode-gated: identity-only fields (def type,
 * rank, unit keywords) are ignored when mode is 'ego'; ego-only field
 * (ego type) is ignored when mode is 'identity'.
 */

import type { DeckFilterState, EntityMode } from '../types/DeckTypes'
import type { IdentityListItem } from '@/pages/identity'
import type { EGOListItem } from '@/pages/ego'
import type { Facet, SearchMappings } from '@/shared/filter'
import { applyFacets } from '@/shared/filter'
import type { Keyword } from '@/shared/gameData'
import { getSinnerFromId } from '@/shared/gameData'

type DeckFilterItem = IdentityListItem | EGOListItem

const DECK_FACETS: readonly Facet<DeckFilterItem, DeckFilterState>[] = [
  { sel: (s) => s.selectedSinners, get: (i) => getSinnerFromId(i.id), mode: 'any' },
  { sel: (s) => s.selectedKeywords, get: (i) => i.skillKeywordList, mode: 'all' },
  { sel: (s) => s.selectedAttributes, get: (i) => i.attributeTypes, mode: 'any' },
  { sel: (s) => s.selectedAtkTypes, get: (i) => i.atkTypes, mode: 'any' },
  { sel: (s) => s.selectedSeasons, get: (i) => i.season, mode: 'any' },
  { sel: (s) => s.selectedBattleKeywords, get: (i) => i.battleKeywordList ?? [], mode: 'any' },
  {
    sel: (s) => (s.entityMode === 'identity' ? s.selectedDefTypes : undefined),
    get: (i) => (i as IdentityListItem).defenseTypes,
    mode: 'any',
  },
  {
    sel: (s) => (s.entityMode === 'identity' ? s.selectedRaritys : undefined),
    get: (i) => (i as IdentityListItem).rank,
    mode: 'any',
  },
  {
    sel: (s) => (s.entityMode === 'identity' ? s.selectedUnitKeywords : undefined),
    get: (i) => (i as IdentityListItem).unitKeywordList,
    mode: 'any',
  },
  {
    sel: (s) => (s.entityMode === 'ego' ? s.selectedEgoTypes : undefined),
    get: (i) => (i as EGOListItem).egoType,
    mode: 'any',
  },
]

/**
 * Evaluates whether an item passes all active deck filters for the given mode.
 *
 * Semantics:
 * - Empty filter sets match everything.
 * - Sinner: derived from entity ID; item's sinner must be in the selected set.
 * - Skill keywords: item must have ALL selected keywords (AND).
 * - Attribute / Atk / Def / Rank / Season / Unit / Battle keywords: ANY match (OR).
 * - EGO type: item.egoType in set (EGO mode only).
 * - Mode gating: identity-only fields skipped when mode is 'ego' and vice versa.
 * - Search: lowercased match against item.name, skill-keyword display names, and
 *   (identity mode only) unit-keyword display names.
 *
 * @param item - IdentityListItem or EGOListItem to evaluate
 * @param state - Current deck filter state (from Zustand slice)
 * @param mode - Entity mode gate; controls which id/ego-specific fields apply
 * @param searchMappings - Reverse mappings from display name to internal codes
 * @returns true if item passes every active filter for this mode
 */
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

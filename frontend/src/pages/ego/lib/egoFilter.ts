/**
 * EGO List Facets
 *
 * Facet descriptors for the EGO browser, plus the per-item predicate the grid's card
 * slots subscribe through.
 */

import type { Facet, SearchMappings } from '@/shared/filter'
import { applyFacets, collectKeywordTerms, matchesSearch } from '@/shared/filter'
import type { FilterState } from '@/components/hooks/useSetFilters'
import type { AtkType, EgoType, Season, SkillAttributeType } from '@/shared/gameData'
import { getSinnerFromId } from '@/shared/gameData'
import type { EGOListItem } from '../types/EGOTypes'

export interface EGOFacetState {
  selectedSinners: ReadonlySet<string>
  selectedKeywords: ReadonlySet<string>
  selectedBattleKeywords: ReadonlySet<string>
  selectedAttributes: ReadonlySet<SkillAttributeType>
  selectedAtkTypes: ReadonlySet<AtkType>
  selectedEGOTypes: ReadonlySet<EgoType>
  selectedSeasons: ReadonlySet<Season>
}

export const EGO_FACETS: readonly Facet<EGOListItem, EGOFacetState>[] = [
  { sel: (s) => s.selectedSinners, get: (e) => getSinnerFromId(e.id), mode: 'any' },
  { sel: (s) => s.selectedKeywords, get: (e) => e.skillKeywordList, mode: 'all' },
  { sel: (s) => s.selectedBattleKeywords, get: (e) => e.battleKeywordList ?? [], mode: 'any' },
  { sel: (s) => s.selectedAttributes, get: (e) => e.attributeTypes, mode: 'all' },
  { sel: (s) => s.selectedAtkTypes, get: (e) => e.atkTypes, mode: 'all' },
  { sel: (s) => s.selectedEGOTypes, get: (e) => e.egoType, mode: 'any' },
  { sel: (s) => s.selectedSeasons, get: (e) => e.season, mode: 'any' },
]

/**
 * Every lowercased string the search box matches an EGO on: its display name plus the
 * natural-language reading of each keyword it carries.
 *
 * Depends only on the i18n payloads, so a filter toggle never invalidates it.
 */
export function buildEGOSearchTerms(
  ego: EGOListItem,
  egoNames: Record<string, string>,
  mappings: SearchMappings,
): string[] {
  return [
    (egoNames[ego.id] ?? '').toLowerCase(),
    ...collectKeywordTerms(mappings.keywordToValue, (value) =>
      ego.skillKeywordList.includes(value),
    ),
  ]
}

/** Whether one EGO survives the current facets and search query. */
export function matchesEGO(
  ego: EGOListItem,
  state: FilterState<EGOFacetState>,
  searchTerms: readonly string[],
): boolean {
  if (!applyFacets(ego, state.values, EGO_FACETS)) return false

  return matchesSearch(state.searchQuery, searchTerms)
}

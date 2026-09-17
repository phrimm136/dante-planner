/**
 * EGO List Facets
 *
 * Facet descriptors for the EGO browser, plus the per-item predicate the grid's card
 * slots subscribe through.
 */

import type { EntityMatcher, Facet, SearchMappings } from '@/shared/filter'
import { collectKeywordTerms, createEntityMatcher } from '@/shared/filter'
import type { AtkType, EgoType, Season, SkillAttributeType } from '@/shared/gameData'
import { getSinnerFromId } from '@/shared/gameData'
import type { EGOEntity } from '../types/EGOTypes'

export interface EGOFacetState {
  selectedSinners: ReadonlySet<string>
  selectedKeywords: ReadonlySet<string>
  selectedBattleKeywords: ReadonlySet<string>
  selectedAttributes: ReadonlySet<SkillAttributeType>
  selectedAtkTypes: ReadonlySet<AtkType>
  selectedEGOTypes: ReadonlySet<EgoType>
  selectedSeasons: ReadonlySet<Season>
}

export const EGO_FACETS: readonly Facet<EGOEntity, EGOFacetState>[] = [
  { sel: (s) => s.selectedSinners, get: (e) => getSinnerFromId(e.id), mode: 'any' },
  { sel: (s) => s.selectedKeywords, get: (e) => e.skillKeywordList, mode: 'all' },
  { sel: (s) => s.selectedBattleKeywords, get: (e) => e.battleKeywordList, mode: 'any' },
  { sel: (s) => s.selectedAttributes, get: (e) => e.attributeType, mode: 'all' },
  { sel: (s) => s.selectedAtkTypes, get: (e) => e.atkType, mode: 'all' },
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
  ego: EGOEntity,
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
export const matchesEGO: EntityMatcher<EGOEntity, EGOFacetState> = createEntityMatcher(EGO_FACETS)

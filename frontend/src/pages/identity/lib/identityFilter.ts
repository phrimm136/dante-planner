/**
 * Identity List Facets
 *
 * Facet descriptors for the identity browser, plus the per-item predicate the grid's
 * card slots subscribe through.
 */

import type { EntityMatcher, Facet, SearchMappings } from '@/shared/filter'
import { collectKeywordTerms, createEntityMatcher } from '@/shared/filter'
import type { AtkType, DefType, Season, SkillAttributeType } from '@/shared/gameData'
import { getSinnerFromId } from '@/shared/gameData'
import type { IdentityListItem } from '../types/IdentityTypes'

export interface IdentityFacetState {
  selectedSinners: ReadonlySet<string>
  selectedKeywords: ReadonlySet<string>
  selectedBattleKeywords: ReadonlySet<string>
  selectedAttributes: ReadonlySet<SkillAttributeType>
  selectedAtkTypes: ReadonlySet<AtkType>
  selectedDefTypes: ReadonlySet<DefType>
  selectedRaritys: ReadonlySet<number>
  selectedSeasons: ReadonlySet<Season>
  selectedUnitKeywords: ReadonlySet<string>
}

export const IDENTITY_FACETS: readonly Facet<IdentityListItem, IdentityFacetState>[] = [
  { sel: (s) => s.selectedSinners, get: (i) => getSinnerFromId(i.id), mode: 'any' },
  { sel: (s) => s.selectedKeywords, get: (i) => i.skillKeywordList, mode: 'all' },
  { sel: (s) => s.selectedBattleKeywords, get: (i) => i.battleKeywordList ?? [], mode: 'any' },
  { sel: (s) => s.selectedAttributes, get: (i) => i.attributeTypes, mode: 'all' },
  { sel: (s) => s.selectedAtkTypes, get: (i) => i.atkTypes, mode: 'all' },
  { sel: (s) => s.selectedDefTypes, get: (i) => i.defenseTypes, mode: 'all' },
  { sel: (s) => s.selectedRaritys, get: (i) => i.rank, mode: 'any' },
  { sel: (s) => s.selectedSeasons, get: (i) => i.season, mode: 'any' },
  { sel: (s) => s.selectedUnitKeywords, get: (i) => i.unitKeywordList, mode: 'any' },
]

/**
 * Every lowercased string the search box matches an identity on: its display name plus
 * the natural-language reading of each keyword and unit keyword it carries.
 *
 * Depends only on the i18n payloads, so a filter toggle never invalidates it.
 */
export function buildIdentitySearchTerms(
  identity: IdentityListItem,
  identityNames: Record<string, string>,
  mappings: SearchMappings,
): string[] {
  return [
    (identityNames[identity.id] ?? '').toLowerCase(),
    ...collectKeywordTerms(mappings.keywordToValue, (value) =>
      identity.skillKeywordList.includes(value),
    ),
    ...collectKeywordTerms(mappings.unitKeywordToValue, (code) =>
      identity.unitKeywordList.includes(code),
    ),
  ]
}

/** Whether one identity survives the current facets and search query. */
export const matchesIdentity: EntityMatcher<IdentityListItem, IdentityFacetState> =
  createEntityMatcher(IDENTITY_FACETS)

import type { EntityMatcher, Facet, SearchMappings } from '@/shared/filter'
import { collectKeywordTerms, createEntityMatcher } from '@/shared/filter'
import type { AtkType, DefType, Season, SkillAttributeType } from '@/shared/gameData'
import { getSinnerFromId } from '@/shared/gameData'
import type { IdentityEntity } from '../types/IdentityTypes'

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

export const IDENTITY_FACETS: readonly Facet<IdentityEntity, IdentityFacetState>[] = [
  { sel: (s) => s.selectedSinners, get: (i) => getSinnerFromId(i.id), mode: 'any' },
  { sel: (s) => s.selectedKeywords, get: (i) => i.skillKeywordList, mode: 'all' },
  { sel: (s) => s.selectedBattleKeywords, get: (i) => i.battleKeywordList, mode: 'any' },
  { sel: (s) => s.selectedAttributes, get: (i) => i.attributeType, mode: 'all' },
  { sel: (s) => s.selectedAtkTypes, get: (i) => i.atkType, mode: 'all' },
  { sel: (s) => s.selectedDefTypes, get: (i) => i.defenseType, mode: 'all' },
  { sel: (s) => s.selectedRaritys, get: (i) => i.rank, mode: 'any' },
  { sel: (s) => s.selectedSeasons, get: (i) => i.season, mode: 'any' },
  { sel: (s) => s.selectedUnitKeywords, get: (i) => i.unitKeywordList, mode: 'any' },
]

export function buildIdentitySearchTerms(
  identity: IdentityEntity,
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

export const matchesIdentity: EntityMatcher<IdentityEntity, IdentityFacetState> =
  createEntityMatcher(IDENTITY_FACETS)

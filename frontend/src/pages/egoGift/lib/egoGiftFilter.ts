/**
 * egoGiftFilter.ts
 *
 * EGO Gift value derivations and facet descriptors, plus the per-item predicate the
 * grid's card slots subscribe through.
 */

import type { Facet, SearchMappings } from '@/shared/filter'
import { applyFacets, collectKeywordTerms, matchesSearch } from '@/shared/filter'
import type { FilterState } from '@/components/hooks/useSetFilters'
import type { EGOGiftAttributeType, EGOGiftDifficulty, EGOGiftTier } from '@/shared/gameData'
import type { EGOGiftListItem } from '../types/EGOGiftTypes'
import { parseTier, toRomanTier } from './egoGiftTier'

/**
 * Derive difficulty from hardOnly/extremeOnly flags
 * Priority: extremeOnly > hardOnly > normal
 *
 * @example
 * deriveDifficulty({ extremeOnly: true }) // Returns 'extreme'
 * deriveDifficulty({ hardOnly: true }) // Returns 'hard'
 * deriveDifficulty({}) // Returns 'normal'
 */
export function deriveDifficulty(gift: {
  hardOnly?: boolean
  extremeOnly?: boolean
}): EGOGiftDifficulty {
  if (gift.extremeOnly) return 'extreme'
  if (gift.hardOnly) return 'hard'
  return 'normal'
}

export interface EGOGiftFacetState {
  selectedKeywords: ReadonlySet<string>
  selectedBattleKeywords: ReadonlySet<string>
  selectedDifficulties: ReadonlySet<EGOGiftDifficulty>
  selectedTiers: ReadonlySet<EGOGiftTier>
  selectedThemePacks: ReadonlySet<string>
  selectedAttributeTypes: ReadonlySet<EGOGiftAttributeType>
  selectedFusioned: ReadonlySet<string>
  selectedExclusive: ReadonlySet<string>
}

export interface EGOGiftSelectionFacetState {
  selectedKeywords: ReadonlySet<string>
}

export const EGO_GIFT_FACETS: readonly Facet<EGOGiftListItem, EGOGiftFacetState>[] = [
  {
    sel: (s) => s.selectedKeywords,
    get: (g) => (g.keyword === null ? 'None' : g.keyword),
    mode: 'any',
  },
  { sel: (s) => s.selectedBattleKeywords, get: (g) => g.battleKeywordList ?? [], mode: 'any' },
  { sel: (s) => s.selectedDifficulties, get: (g) => deriveDifficulty(g), mode: 'any' },
  { sel: (s) => s.selectedTiers, get: (g) => toRomanTier(parseTier(g.tag)), mode: 'any' },
  { sel: (s) => s.selectedThemePacks, get: (g) => (g.themePack ?? []).map(String), mode: 'any' },
  { sel: (s) => s.selectedAttributeTypes, get: (g) => g.attributeType, mode: 'any' },
  { sel: (s) => s.selectedFusioned, get: (g) => (g.fusioned === true ? 'Y' : 'N'), mode: 'any' },
  {
    sel: (s) => s.selectedExclusive,
    get: (g) => ((g.themePack ?? []).length > 0 ? 'Y' : 'N'),
    mode: 'any',
  },
]

export const EGO_GIFT_SELECTION_FACETS: readonly Facet<
  EGOGiftListItem,
  EGOGiftSelectionFacetState
>[] = [{ sel: (s) => s.selectedKeywords, get: (g) => g.keyword ?? 'None', mode: 'any' }]

/**
 * Every lowercased string the search box matches a gift on: its display name plus the
 * natural-language reading of the keyword it carries.
 *
 * Depends only on the i18n payloads, so a filter toggle never invalidates it.
 */
export function buildEGOGiftSearchTerms(
  gift: EGOGiftListItem,
  giftNames: Record<string, string>,
  mappings: SearchMappings,
): string[] {
  const { keyword } = gift
  const terms = [(giftNames[gift.id] ?? '').toLowerCase()]

  if (keyword) {
    terms.push(...collectKeywordTerms(mappings.keywordToValue, (value) => value === keyword))
  }

  return terms
}

/** Whether one gift survives the current facets and search query. */
export function matchesEGOGift(
  gift: EGOGiftListItem,
  state: FilterState<EGOGiftFacetState>,
  searchTerms: readonly string[],
): boolean {
  if (!applyFacets(gift, state.values, EGO_GIFT_FACETS)) return false

  return matchesSearch(state.searchQuery, searchTerms)
}

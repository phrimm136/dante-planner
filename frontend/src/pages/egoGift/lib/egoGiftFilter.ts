/**
 * egoGiftFilter.ts
 *
 * EGO Gift value derivations and facet descriptors, plus the per-item predicate the
 * grid's card slots subscribe through.
 */

import type { Facet, SearchMappings } from '@/shared/filter'
import { applyFacets } from '@/shared/filter'
import type { FilterState } from '@/components/hooks/useSetFilters'
import type { EGOGiftAttributeType, EGOGiftDifficulty, EGOGiftTier } from '@/shared/gameData'
import { EGO_GIFT_TIERS, EGO_GIFT_TIER_TAGS } from '@/shared/gameData'
import type { EGOGiftListItem } from '../types/EGOGiftTypes'

/** Map tier tag (TIER_1) to display tier (I) */
const TIER_TAG_TO_DISPLAY: Record<string, EGOGiftTier> = Object.fromEntries(
  EGO_GIFT_TIER_TAGS.map((tag, index) => [tag, EGO_GIFT_TIERS[index]]),
)

/**
 * Extract display tier from gift tag array
 * Returns the first matching tier or undefined
 *
 * @example
 * extractTier(['TIER_3', 'GIFT']) // Returns 'III'
 * extractTier(['GIFT']) // Returns undefined
 */
export function extractTier(tag: readonly string[]): EGOGiftTier | undefined {
  for (const t of tag) {
    const tier = TIER_TAG_TO_DISPLAY[t]
    if (tier) return tier
  }
  return undefined
}

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
  { sel: (s) => s.selectedTiers, get: (g) => extractTier(g.tag), mode: 'any' },
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
  const terms = [(giftNames[gift.id] ?? '').toLowerCase()]

  for (const [naturalLang, pascalValues] of mappings.keywordToValue) {
    if (gift.keyword && pascalValues.includes(gift.keyword)) {
      terms.push(naturalLang)
    }
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
  if (!state.searchQuery) return true

  const lowerQuery = state.searchQuery.toLowerCase()
  return searchTerms.some((term) => term.includes(lowerQuery))
}

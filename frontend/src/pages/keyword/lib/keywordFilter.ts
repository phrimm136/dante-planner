/**
 * Keyword List Facets
 *
 * Facet descriptors for the keyword browser, plus the per-item predicate the grid's
 * card slots subscribe through.
 */

import type { EntityMatcher, Facet } from '@/shared/filter'
import { createEntityMatcher } from '@/shared/filter'
import type { BuffType } from '@/shared/gameData'
import type { BattleKeywordI18nEntry } from '@/shared/gameText'

export interface KeywordFacetItem {
  buffType: string
  identities: string[]
  egos: string[]
  egoGifts: string[]
}

export interface KeywordFacetState {
  selectedBuffTypes: ReadonlySet<BuffType>
  selectedIdentities: ReadonlySet<string>
  selectedEgos: ReadonlySet<string>
  selectedEgoGifts: ReadonlySet<string>
}

export const KEYWORD_FACETS: readonly Facet<KeywordFacetItem, KeywordFacetState>[] = [
  { sel: (s) => s.selectedBuffTypes, get: (k) => k.buffType, mode: 'any' },
  { sel: (s) => s.selectedIdentities, get: (k) => k.identities, mode: 'any' },
  { sel: (s) => s.selectedEgos, get: (k) => k.egos, mode: 'any' },
  { sel: (s) => s.selectedEgoGifts, get: (k) => k.egoGifts, mode: 'any' },
]

/**
 * Every lowercased string the search box matches a keyword on: its localized name.
 *
 * Depends only on the i18n payload, so a filter toggle never invalidates it.
 */
export function buildKeywordSearchTerms(
  keywordId: string,
  keywordNames: Record<string, BattleKeywordI18nEntry>,
): string[] {
  return [(keywordNames[keywordId]?.name ?? '').toLowerCase()]
}

/** Whether one keyword survives the current facets and search query. */
export const matchesKeyword: EntityMatcher<KeywordFacetItem, KeywordFacetState> =
  createEntityMatcher(KEYWORD_FACETS)

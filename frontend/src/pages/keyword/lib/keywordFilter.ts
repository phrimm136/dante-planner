import type { EntityMatcher, Facet } from '@/shared/filter'
import { createEntityMatcher } from '@/shared/filter'
import type { BuffType } from '@/shared/gameData'
import type { BattleKeywordI18nEntry } from '@/shared/gameText'
import type { KeywordEntity } from '../types/KeywordTypes'

export interface KeywordFacetState {
  selectedBuffTypes: ReadonlySet<BuffType>
  selectedIdentities: ReadonlySet<string>
  selectedEgos: ReadonlySet<string>
  selectedEgoGifts: ReadonlySet<string>
}

export const KEYWORD_FACETS: readonly Facet<KeywordEntity, KeywordFacetState>[] = [
  { sel: (s) => s.selectedBuffTypes, get: (k) => k.buffType, mode: 'any' },
  { sel: (s) => s.selectedIdentities, get: (k) => k.identities, mode: 'any' },
  { sel: (s) => s.selectedEgos, get: (k) => k.egos, mode: 'any' },
  { sel: (s) => s.selectedEgoGifts, get: (k) => k.egoGifts, mode: 'any' },
]

export function buildKeywordSearchTerms(
  keywordId: string,
  keywordNames: Record<string, BattleKeywordI18nEntry>,
): string[] {
  return [(keywordNames[keywordId]?.name ?? '').toLowerCase()]
}

export const matchesKeyword: EntityMatcher<KeywordEntity, KeywordFacetState> =
  createEntityMatcher(KEYWORD_FACETS)

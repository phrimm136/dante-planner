import type { z } from 'zod'
import { createEntityListQueryKeys } from '@/lib/queryKeys'
import {
  useEntityListData,
  useEntityListI18n,
  useEntityListI18nDeferred,
  useEntityListSpec,
  type EntityListDataConfig,
} from '@/shared/entityCatalog'
import { BattleKeywordSpecListSchema } from '../schemas/KeywordSchemas'
import { BattleKeywordsSchema } from '../schemas/BattleKeywordsSchemas'
import type { BattleKeywordSpecEntry } from '../types/KeywordTypes'
import type { BattleKeywordI18nEntry } from '../types/StartBuffTypes'

export const keywordListQueryKeys = createEntityListQueryKeys('keyword')

export const KEYWORD_LIST: EntityListDataConfig<
  z.infer<typeof BattleKeywordSpecListSchema>,
  z.infer<typeof BattleKeywordsSchema>
> = {
  kind: 'keyword',
  specImport: () => import('@static/data/battleKeywordSpecList.json'),
  specSchema: BattleKeywordSpecListSchema,
  i18nImport: (language) => import(`@static/i18n/${language}/battleKeywords.json`),
  i18nSchema: BattleKeywordsSchema,
  emptyI18n: {},
}

/**
 * Hook that loads keyword spec list only (no language dependency)
 * Suspends on initial load, but NOT on language change (key has no language)
 *
 * Use this in shell components that should stay stable during language change.
 *
 * @returns Validated keyword spec map (id -> BattleKeywordSpecEntry)
 */
export function useKeywordListSpec(): Record<string, BattleKeywordSpecEntry> {
  return useEntityListSpec(KEYWORD_LIST)
}

/**
 * Hook that loads and validates keyword i18n list only
 * Suspends while loading - wrap in Suspense boundary
 *
 * Use this in components wrapped in their own Suspense boundary
 * for granular loading states on language change.
 *
 * @returns Validated keyword i18n map (id -> { name, desc })
 */
export function useKeywordListI18n(): Record<string, BattleKeywordI18nEntry> {
  return useEntityListI18n(KEYWORD_LIST)
}

/**
 * Non-suspending version of useKeywordListI18n for list filtering.
 * Returns empty object while loading - name search won't match anything.
 * Use this in list components to prevent suspension during language change.
 *
 * @returns Keyword i18n map (id -> { name, desc }), empty object while loading
 */
export function useKeywordListI18nDeferred(): Record<string, BattleKeywordI18nEntry> {
  return useEntityListI18nDeferred(KEYWORD_LIST)
}

/**
 * Hook that loads and validates keyword list data (spec list + i18n)
 * Suspends while loading - wrap in Suspense boundary
 *
 * Returns spec map and i18n map separately for flexible consumption.
 *
 * @returns Validated keyword spec map and i18n map
 */
export function useKeywordListData(): {
  spec: Record<string, BattleKeywordSpecEntry>
  i18n: Record<string, BattleKeywordI18nEntry>
} {
  return useEntityListData(KEYWORD_LIST)
}

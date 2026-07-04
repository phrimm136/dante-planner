import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { BattleKeywordSpecListSchema, BattleKeywordsSchema } from '@/shared/gameText'
import type { BattleKeywordSpecEntry } from '@/shared/gameText'
import type { BattleKeywordI18nEntry } from '@/shared/gameText'
import { keywordListQueryKeys } from '@/shared/gameText'

// Keyword spec list query options (shared with list hooks via same query key)
function createKeywordSpecListQueryOptions() {
  return createStaticDataQueryOptions(
    keywordListQueryKeys.spec(),
    () => import('@static/data/battleKeywordSpecList.json'),
    BattleKeywordSpecListSchema,
    'keyword specList',
  )
}

// Keyword i18n query options (shared with list hooks via same query key)
function createKeywordI18nQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    keywordListQueryKeys.i18n(language),
    () => import(`@static/i18n/${language}/battleKeywords.json`),
    BattleKeywordsSchema,
    `keyword i18n / ${language}`,
  )
}

/**
 * Hook that loads a single keyword spec entry by ID
 * Derives from the full spec list (shares cache with useKeywordListSpec)
 * Suspends on initial load - wrap in Suspense boundary
 *
 * @param id - Keyword ID (e.g., "Combustion")
 * @returns Single keyword spec entry, or undefined if not found
 */
export function useKeywordDetailSpec(id: string): BattleKeywordSpecEntry | undefined {
  const { data: specList } = useSuspenseQuery(createKeywordSpecListQueryOptions())
  return specList[id]
}

/**
 * Hook that loads a single keyword i18n entry by ID
 * Derives from the full i18n list (shares cache with useKeywordListI18n)
 * Suspends while loading - wrap in Suspense boundary
 *
 * @param id - Keyword ID (e.g., "Combustion")
 * @returns Single keyword i18n entry (name + desc), or undefined if not found
 */
export function useKeywordDetailI18n(id: string): BattleKeywordI18nEntry | undefined {
  const { i18n } = useTranslation()
  const { data: i18nList } = useSuspenseQuery(createKeywordI18nQueryOptions(i18n.language))
  return i18nList[id]
}

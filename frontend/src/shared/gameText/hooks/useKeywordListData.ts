import { useSuspenseQuery, useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createEntityListQueryKeys } from '@/lib/queryKeys'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { BattleKeywordSpecListSchema } from '../schemas/KeywordSchemas'
import { BattleKeywordsSchema } from '../schemas/BattleKeywordsSchemas'
import type { BattleKeywordSpecEntry } from '../types/KeywordTypes'
import type { BattleKeywordI18nEntry } from '../types/StartBuffTypes'

export const keywordListQueryKeys = createEntityListQueryKeys('keyword')

function createKeywordSpecListQueryOptions() {
  return createStaticDataQueryOptions(
    keywordListQueryKeys.spec(),
    () => import('@static/data/battleKeywordSpecList.json'),
    BattleKeywordSpecListSchema,
    'keyword specList',
  )
}

function createKeywordI18nQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    keywordListQueryKeys.i18n(language),
    () => import(`@static/i18n/${language}/battleKeywords.json`),
    BattleKeywordsSchema,
    `keyword i18n / ${language}`,
    { keepPrevious: true },
  )
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
  const { data: spec } = useSuspenseQuery(createKeywordSpecListQueryOptions())
  return spec
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
  const { i18n } = useTranslation()
  const { data: i18nData } = useSuspenseQuery(
    createKeywordI18nQueryOptions(i18n.language)
  )
  return i18nData
}

// Empty i18n list constant for loading state
const EMPTY_I18N_LIST: Record<string, BattleKeywordI18nEntry> = {}

/**
 * Non-suspending version of useKeywordListI18n for list filtering.
 * Returns empty object while loading - name search won't match anything.
 * Use this in list components to prevent suspension during language change.
 *
 * @returns Keyword i18n map (id -> { name, desc }), empty object while loading
 */
export function useKeywordListI18nDeferred(): Record<string, BattleKeywordI18nEntry> {
  const { i18n } = useTranslation()
  const { data: i18nData } = useQuery(
    createKeywordI18nQueryOptions(i18n.language)
  )
  return i18nData ?? EMPTY_I18N_LIST
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
  const { i18n } = useTranslation()

  const { data: spec } = useSuspenseQuery(createKeywordSpecListQueryOptions())
  const { data: i18nData } = useSuspenseQuery(
    createKeywordI18nQueryOptions(i18n.language)
  )

  return {
    spec,
    i18n: i18nData,
  }
}

import { useSuspenseQuery, useQuery, queryOptions, keepPreviousData } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { BattleKeywordSpecListSchema, BattleKeywordsSchema } from '@/schemas'
import type { BattleKeywordSpecEntry } from '@/types/KeywordTypes'
import type { BattleKeywordI18nEntry } from '@/types/StartBuffTypes'

// Query key factory for keyword list data
export const keywordListQueryKeys = {
  all: () => ['keyword', 'list'] as const,
  spec: () => ['keyword', 'list', 'spec'] as const,
  i18n: (language: string) => ['keyword', 'list', 'i18n', language] as const,
}

// Keyword spec list query options with runtime validation
function createKeywordSpecListQueryOptions() {
  return queryOptions({
    queryKey: keywordListQueryKeys.spec(),
    queryFn: async () => {
      const module = await import('@static/data/battleKeywordSpecList.json')
      const result = BattleKeywordSpecListSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[keyword specList] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

// Keyword i18n query options with runtime validation
function createKeywordI18nQueryOptions(language: string) {
  return queryOptions({
    queryKey: keywordListQueryKeys.i18n(language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/battleKeywords.json`)
      const result = BattleKeywordsSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[keyword i18n / ${language}] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
    placeholderData: keepPreviousData,
  })
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

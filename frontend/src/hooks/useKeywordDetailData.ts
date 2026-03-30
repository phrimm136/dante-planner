import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { BattleKeywordSpecListSchema, BattleKeywordsSchema } from '@/schemas'
import type { BattleKeywordSpecEntry } from '@/types/KeywordTypes'
import type { BattleKeywordI18nEntry } from '@/types/StartBuffTypes'
import { keywordListQueryKeys } from '@/hooks/useKeywordListData'

// Keyword spec list query options (shared with list hooks via same query key)
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

// Keyword i18n query options (shared with list hooks via same query key)
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
  })
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
  const { data: i18nList } = useSuspenseQuery(
    createKeywordI18nQueryOptions(i18n.language)
  )
  return i18nList[id]
}

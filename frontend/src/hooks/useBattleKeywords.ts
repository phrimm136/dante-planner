import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { BattleKeywords } from '@/types/StartBuffTypes'
import { BattleKeywordsSchema } from '@/schemas'

// Query key factory for battle keywords
export const battleKeywordsQueryKeys = {
  all: ['battleKeywords'] as const,
  byLanguage: (language: string) => [...battleKeywordsQueryKeys.all, language] as const,
}

// Battle keywords query options with validation
function createBattleKeywordsQueryOptions(language: string) {
  return queryOptions({
    queryKey: battleKeywordsQueryKeys.byLanguage(language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/battleKeywords.json`)
      const result = BattleKeywordsSchema.safeParse(module.default)

      if (!result.success) {
        const isDev = import.meta.env.DEV
        if (isDev) {
          console.error('[battleKeywords] Validation failed:', result.error.issues)
        }
        throw new Error(`[battleKeywords / ${language}] Invalid data structure`)
      }

      return result.data as BattleKeywords
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads battle keywords with i18n translations
 * Suspends while loading - wrap in Suspense boundary
 * Used for translating buff keywords like ParryingResultUp, AttackDmgUp, Protection
 */
export function useBattleKeywords() {
  const { i18n } = useTranslation()
  const { data } = useSuspenseQuery(createBattleKeywordsQueryOptions(i18n.language))
  return { data }
}

/**
 * Gets translated keyword name from battle keywords data
 * @param keywords - Battle keywords dictionary (validated)
 * @param key - Keyword key (e.g., "ParryingResultUp")
 * @returns Translated name or original key if not found
 */
export function getKeywordName(keywords: BattleKeywords, key: string): string {
  return keywords[key]?.name ?? key
}

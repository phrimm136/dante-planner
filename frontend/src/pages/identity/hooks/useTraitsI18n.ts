import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { UnitKeywordsSchema } from '@/schemas'

// Query key factory for traits i18n data
export const traitsI18nQueryKeys = {
  all: () => ['traits'] as const,
  i18n: (language: string) => ['traits', 'i18n', language] as const,
}

// Traits i18n query options with runtime validation
function createTraitsI18nQueryOptions(language: string) {
  return queryOptions({
    queryKey: traitsI18nQueryKeys.i18n(language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/unitKeywords.json`)
      const result = UnitKeywordsSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[traits i18n / ${language}] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads and validates trait names (unitKeywords.json)
 * Suspends while loading - wrap in Suspense boundary
 *
 * Query key includes language so it suspends on language change.
 * Use this in components wrapped in their own Suspense boundary
 * for granular loading states on language change.
 *
 * @returns Validated trait name map (trait ID -> translated name)
 */
export function useTraitsI18n(): Record<string, string> {
  const { i18n } = useTranslation()
  const { data: traitsI18n } = useSuspenseQuery(
    createTraitsI18nQueryOptions(i18n.language)
  )
  return traitsI18n
}

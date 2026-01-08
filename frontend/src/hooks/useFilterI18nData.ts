import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { SeasonsI18nSchema, UnitKeywordsI18nSchema } from '@/schemas'

// Query key factory for filter i18n data
export const filterI18nQueryKeys = {
  all: () => ['filter', 'i18n'] as const,
  seasons: (language: string) => ['filter', 'i18n', 'seasons', language] as const,
  unitKeywords: (language: string) => ['filter', 'i18n', 'unitKeywords', language] as const,
}

// Seasons i18n query options with runtime validation
function createSeasonsI18nQueryOptions(language: string) {
  return queryOptions({
    queryKey: filterI18nQueryKeys.seasons(language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/seasons.json`)
      const result = SeasonsI18nSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[seasons i18n] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

// UnitKeywords i18n query options with runtime validation
function createUnitKeywordsI18nQueryOptions(language: string) {
  return queryOptions({
    queryKey: filterI18nQueryKeys.unitKeywords(language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/unitKeywords.json`)
      const result = UnitKeywordsI18nSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[unitKeywords i18n] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads and validates filter i18n data (seasons + unitKeywords)
 * Suspends while loading - wrap in Suspense boundary
 *
 * @returns Validated seasons and unitKeywords i18n data
 */
export function useFilterI18nData() {
  const { i18n } = useTranslation()

  const { data: seasonsI18n } = useSuspenseQuery(
    createSeasonsI18nQueryOptions(i18n.language)
  )
  const { data: unitKeywordsI18n } = useSuspenseQuery(
    createUnitKeywordsI18nQueryOptions(i18n.language)
  )

  return {
    seasonsI18n,
    unitKeywordsI18n,
  }
}

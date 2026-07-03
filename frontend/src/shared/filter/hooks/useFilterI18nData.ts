import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { SeasonsI18nSchema, UnitKeywordsI18nSchema } from '../schemas/FilterSchemas'

// Query key factory for filter i18n data
// Hand-rolled: tuples deviate from the shared list/detail factory shapes
export const filterI18nQueryKeys = {
  all: () => ['filter', 'i18n'] as const,
  seasons: (language: string) => ['filter', 'i18n', 'seasons', language] as const,
  unitKeywords: (language: string) => ['filter', 'i18n', 'unitKeywords', language] as const,
}

function createSeasonsI18nQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    filterI18nQueryKeys.seasons(language),
    () => import(`@static/i18n/${language}/seasons.json`),
    SeasonsI18nSchema,
    'seasons i18n',
  )
}

function createUnitKeywordsI18nQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    filterI18nQueryKeys.unitKeywords(language),
    () => import(`@static/i18n/${language}/unitKeywords.json`),
    UnitKeywordsI18nSchema,
    'unitKeywords i18n',
  )
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

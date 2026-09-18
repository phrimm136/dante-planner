import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { SeasonsI18nSchema } from '../schemas/FilterSchemas'
import { useUnitKeywords } from './useUnitKeywords'

export const filterI18nQueryKeys = {
  all: () => ['filter', 'i18n'] as const,
  seasons: (language: string) => ['filter', 'i18n', 'seasons', language] as const,
}

function createSeasonsI18nQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    filterI18nQueryKeys.seasons(language),
    () => import(`@static/i18n/${language}/seasons.json`),
    SeasonsI18nSchema,
    'seasons i18n',
  )
}

export function useFilterI18nData() {
  const { i18n } = useTranslation()

  const { data: seasonsI18n } = useSuspenseQuery(createSeasonsI18nQueryOptions(i18n.language))
  const unitKeywordsI18n = useUnitKeywords()

  return {
    seasonsI18n,
    unitKeywordsI18n,
  }
}

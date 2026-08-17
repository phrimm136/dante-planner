import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { UnitKeywordsSchema } from '../schemas/SearchMappingSchemas'
import type { UnitKeywords } from '../schemas/SearchMappingSchemas'

/**
 * Query keys for the unit keyword name map.
 * Hand-rolled: tuple lacks the 'list' segment the shared factory produces
 */
export const unitKeywordsQueryKeys = {
  i18n: (language: string) => ['unitKeywords', 'i18n', language] as const,
}

export function createUnitKeywordsQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    unitKeywordsQueryKeys.i18n(language),
    async () => {
      try {
        return await import(`@static/i18n/${language}/unitKeywords.json`)
      } catch {
        // Missing language file falls back to empty mappings, not an error
        return { default: {} }
      }
    },
    UnitKeywordsSchema,
    `unitKeywords / ${language}`,
  )
}

/**
 * Unit keyword code → localized name, for the active language.
 *
 * Sole owner of `unitKeywords.json`: the filter dropdown, the trait list and
 * search all read it from here, so one fetch and one cache entry serve them
 * instead of one apiece. Suspends while loading — wrap in a Suspense boundary.
 */
export function useUnitKeywords(): UnitKeywords {
  const { i18n } = useTranslation()
  const { data } = useSuspenseQuery(createUnitKeywordsQueryOptions(i18n.language))
  return data
}

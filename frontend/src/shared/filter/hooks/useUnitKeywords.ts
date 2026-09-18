import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { UnitKeywordsSchema } from '../schemas/SearchMappingSchemas'
import type { UnitKeywords } from '../schemas/SearchMappingSchemas'

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
        return { default: {} }
      }
    },
    UnitKeywordsSchema,
    `unitKeywords / ${language}`,
  )
}

export function useUnitKeywords(): UnitKeywords {
  const { i18n } = useTranslation()
  const { data } = useSuspenseQuery(createUnitKeywordsQueryOptions(i18n.language))
  return data
}

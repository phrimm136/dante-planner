import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { SanityConditionI18nSchema } from '../schemas/SanityConditionSchemas'
import type { SanityConditionI18n } from '../schemas/SanityConditionSchemas'

export const sanityConditionQueryKeys = {
  i18n: (language: string) => ['sanityCondition', 'i18n', language] as const,
}

function createSanityConditionI18nQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    sanityConditionQueryKeys.i18n(language),
    () => import(`@static/i18n/${language}/sanityCondition.json`),
    SanityConditionI18nSchema,
    `sanityCondition i18n / ${language}`,
  )
}

export function useSanityConditionI18n(): SanityConditionI18n {
  const { i18n } = useTranslation()
  const { data } = useSuspenseQuery(createSanityConditionI18nQueryOptions(i18n.language))
  return data
}

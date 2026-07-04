import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { SanityConditionI18nSchema } from '../schemas/SanityConditionSchemas'
import type { SanityConditionI18n } from '../schemas/SanityConditionSchemas'

/**
 * Query keys for sanity condition i18n data
 * Hand-rolled: tuple lacks the 'list' segment the shared factory produces
 */
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

/**
 * Hook that loads and validates sanity condition i18n data.
 * Suspends while loading - wrap in Suspense boundary.
 *
 * Returns the raw i18n data for manual formatting.
 * For formatting, use useSanityConditionFormatter from lib/sanityConditionFormatter.ts
 *
 * @returns Validated sanity condition i18n data
 */
export function useSanityConditionI18n(): SanityConditionI18n {
  const { i18n } = useTranslation()
  const { data } = useSuspenseQuery(createSanityConditionI18nQueryOptions(i18n.language))
  return data
}

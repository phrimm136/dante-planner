import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { UnitKeywordsSchema } from '@/shared/filter'

// Query key factory for traits i18n data
// Hand-rolled: tuple lacks the 'list' segment the shared factory produces
export const traitsI18nQueryKeys = {
  all: () => ['traits'] as const,
  i18n: (language: string) => ['traits', 'i18n', language] as const,
}

function createTraitsI18nQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    traitsI18nQueryKeys.i18n(language),
    () => import(`@static/i18n/${language}/unitKeywords.json`),
    UnitKeywordsSchema,
    `traits i18n / ${language}`,
  )
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
  const { data: traitsI18n } = useSuspenseQuery(createTraitsI18nQueryOptions(i18n.language))
  return traitsI18n
}

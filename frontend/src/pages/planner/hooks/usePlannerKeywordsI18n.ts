import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'

const PlannerKeywordI18nEntrySchema = z.object({
  label: z.string(),
})

const PlannerKeywordsI18nSchema = z.record(z.string(), PlannerKeywordI18nEntrySchema)

export type PlannerKeywordsI18n = z.infer<typeof PlannerKeywordsI18nSchema>

export const plannerKeywordsQueryKeys = {
  i18n: (language: string) => ['plannerKeywords', 'i18n', language] as const,
}

function createPlannerKeywordsI18nQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    plannerKeywordsQueryKeys.i18n(language),
    () => import(`@static/i18n/${language}/plannerKeywords.json`),
    PlannerKeywordsI18nSchema,
    `plannerKeywords i18n / ${language}`,
  )
}

export function usePlannerKeywordsI18n(): PlannerKeywordsI18n {
  const { i18n } = useTranslation()
  const { data } = useSuspenseQuery(createPlannerKeywordsI18nQueryOptions(i18n.language))
  return data
}

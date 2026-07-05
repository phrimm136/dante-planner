/**
 * Planner Keywords i18n Hook
 *
 * Loads localized display names for planner keywords (plannerKeywords.json).
 * Pattern: Same as useIdentityListData (dynamic import + Zod validation + TanStack Query)
 */

import { useSuspenseQuery, useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'

// ============================================================================
// Schema
// ============================================================================

const PlannerKeywordI18nEntrySchema = z.object({
  label: z.string(),
})

const PlannerKeywordsI18nSchema = z.record(z.string(), PlannerKeywordI18nEntrySchema)

export type PlannerKeywordsI18n = z.infer<typeof PlannerKeywordsI18nSchema>

// ============================================================================
// Query Options
// ============================================================================

// Hand-rolled: tuple lacks the 'list' segment the shared factory produces
export const plannerKeywordsQueryKeys = {
  i18n: (language: string) => ['plannerKeywords', 'i18n', language] as const,
}

function createPlannerKeywordsI18nQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    plannerKeywordsQueryKeys.i18n(language),
    () => import(`@static/i18n/${language}/plannerKeywords.json`),
    PlannerKeywordsI18nSchema,
    `plannerKeywords i18n / ${language}`,
    { keepPrevious: true },
  )
}

// ============================================================================
// Hooks
// ============================================================================

/**
 * Suspending hook for planner keyword i18n data.
 * Wrap in Suspense boundary.
 *
 * @returns Planner keywords i18n map (keyword -> { label })
 */
export function usePlannerKeywordsI18n(): PlannerKeywordsI18n {
  const { i18n } = useTranslation()
  const { data } = useSuspenseQuery(createPlannerKeywordsI18nQueryOptions(i18n.language))
  return data
}

const EMPTY_MAP: PlannerKeywordsI18n = {}

/**
 * Non-suspending version for contexts where suspension is undesirable.
 * Returns empty object while loading.
 *
 * @returns Planner keywords i18n map, empty object while loading
 */
export function usePlannerKeywordsI18nDeferred(): PlannerKeywordsI18n {
  const { i18n } = useTranslation()
  const { data } = useQuery(createPlannerKeywordsI18nQueryOptions(i18n.language))
  return data ?? EMPTY_MAP
}

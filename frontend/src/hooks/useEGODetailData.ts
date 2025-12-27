import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { EGODataSchema, EGOI18nSchema } from '@/schemas'

// Query key factory for EGO detail data
export const egoDetailQueryKeys = {
  all: () => ['ego'] as const,
  detail: (id: string) => ['ego', id] as const,
  i18n: (id: string, language: string) => ['ego', id, 'i18n', language] as const,
}

// EGO data query options with runtime validation
function createEGODataQueryOptions(id: string) {
  return queryOptions({
    queryKey: egoDetailQueryKeys.detail(id),
    queryFn: async () => {
      const module = await import(`@static/data/ego/${id}.json`)
      const result = EGODataSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[ego / ${id}] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

// EGO i18n query options with runtime validation
function createEGOI18nQueryOptions(id: string, language: string) {
  return queryOptions({
    queryKey: egoDetailQueryKeys.i18n(id, language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/ego/${id}.json`)
      const result = EGOI18nSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[ego i18n / ${id} / ${language}] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads and validates EGO detail data (spec + i18n)
 * Suspends while loading - wrap in Suspense boundary
 *
 * Returns strongly typed EGOData and EGOI18n without requiring type assertions.
 *
 * @param id - EGO ID (must be defined - validate in route first)
 * @returns Validated EGO data and i18n
 */
export function useEGODetailData(id: string) {
  const { i18n } = useTranslation()

  const { data: spec } = useSuspenseQuery(createEGODataQueryOptions(id))
  const { data: i18nData } = useSuspenseQuery(
    createEGOI18nQueryOptions(id, i18n.language)
  )

  return {
    spec,
    i18n: i18nData,
  }
}

import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { EGOGiftDataSchema, EGOGiftI18nSchema } from '@/schemas'
import queryConfig from '@static/config/queryConfig.json'

// Query key factory for EGO Gift detail data
export const egoGiftDetailQueryKeys = {
  all: () => ['egoGift'] as const,
  detail: (id: string) => ['egoGift', id] as const,
  i18n: (id: string, language: string) => ['egoGift', id, 'i18n', language] as const,
}

// EGO Gift data query options with runtime validation
function createEGOGiftDataQueryOptions(id: string) {
  return queryOptions({
    queryKey: egoGiftDetailQueryKeys.detail(id),
    queryFn: async () => {
      const module = await import(`@static/data/egoGift/${id}.json`)
      const result = EGOGiftDataSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[egoGift / ${id}] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: queryConfig.staleTime.egoGift,
  })
}

// EGO Gift i18n query options with runtime validation
function createEGOGiftI18nQueryOptions(id: string, language: string) {
  return queryOptions({
    queryKey: egoGiftDetailQueryKeys.i18n(id, language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/egoGift/${id}.json`)
      const result = EGOGiftI18nSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[egoGift i18n / ${id} / ${language}] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads and validates EGO Gift detail data (spec + i18n)
 * Suspends while loading - wrap in Suspense boundary
 *
 * Returns strongly typed EGOGiftData and EGOGiftI18n without requiring type assertions.
 *
 * @param id - EGO Gift ID (must be defined - validate in route first)
 * @returns Validated EGO Gift data and i18n
 */
export function useEGOGiftDetailData(id: string) {
  const { i18n } = useTranslation()

  const { data: spec } = useSuspenseQuery(createEGOGiftDataQueryOptions(id))
  const { data: i18nData } = useSuspenseQuery(
    createEGOGiftI18nQueryOptions(id, i18n.language)
  )

  return {
    spec,
    i18n: i18nData,
  }
}

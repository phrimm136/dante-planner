import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { EGOGiftDataSchema, EGOGiftI18nSchema } from '@/schemas'

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
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
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
 * Hook that loads EGO Gift spec data only (no language dependency)
 * Suspends on initial load, but NOT on language change (key has no language)
 *
 * Use this in shell components that should stay stable during language change.
 *
 * @param id - EGO Gift ID (must be defined - validate in route first)
 * @returns Validated EGO Gift spec data
 */
export function useEGOGiftDetailSpec(id: string) {
  const { data: spec } = useSuspenseQuery(createEGOGiftDataQueryOptions(id))
  return spec
}

/**
 * Hook that loads EGO Gift i18n data only
 * Suspends while loading - wrap in Suspense boundary
 *
 * Use this in components wrapped in their own Suspense boundary
 * for granular loading states on language change.
 *
 * @param id - EGO Gift ID (must be defined - validate in route first)
 * @returns Validated EGO Gift i18n data
 */
export function useEGOGiftDetailI18n(id: string) {
  const { i18n } = useTranslation()
  const { data: i18nData } = useSuspenseQuery(
    createEGOGiftI18nQueryOptions(id, i18n.language)
  )
  return i18nData
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

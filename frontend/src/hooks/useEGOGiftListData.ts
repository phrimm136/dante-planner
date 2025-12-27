import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { EGOGiftSpecListSchema, EGOGiftNameListSchema } from '@/schemas'
import queryConfig from '@static/config/queryConfig.json'

// Query key factory for EGO Gift list data
export const egoGiftListQueryKeys = {
  all: () => ['egoGift', 'list'] as const,
  spec: () => ['egoGift', 'list', 'spec'] as const,
  i18n: (language: string) => ['egoGift', 'list', 'i18n', language] as const,
}

// EGO Gift spec list query options with runtime validation
function createEGOGiftSpecListQueryOptions() {
  return queryOptions({
    queryKey: egoGiftListQueryKeys.spec(),
    queryFn: async () => {
      const module = await import('@static/data/egoGiftSpecList.json')
      const result = EGOGiftSpecListSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[egoGift specList] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: queryConfig.staleTime.egoGift,
  })
}

// EGO Gift name list query options with runtime validation
function createEGOGiftNameListQueryOptions(language: string) {
  return queryOptions({
    queryKey: egoGiftListQueryKeys.i18n(language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/egoGiftNameList.json`)
      const result = EGOGiftNameListSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[egoGift nameList / ${language}] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads and validates EGO Gift list data (spec list + name list)
 * Suspends while loading - wrap in Suspense boundary
 *
 * Returns spec map and name map separately for flexible consumption.
 *
 * @returns Validated EGO Gift spec map and name map
 */
export function useEGOGiftListData() {
  const { i18n } = useTranslation()

  const { data: spec } = useSuspenseQuery(createEGOGiftSpecListQueryOptions())
  const { data: i18nData } = useSuspenseQuery(
    createEGOGiftNameListQueryOptions(i18n.language)
  )

  return {
    spec,
    i18n: i18nData,
  }
}

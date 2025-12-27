import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { EGOSpecListSchema, EGONameListSchema } from '@/schemas'

// Query key factory for EGO list data
export const egoListQueryKeys = {
  all: () => ['ego', 'list'] as const,
  spec: () => ['ego', 'list', 'spec'] as const,
  i18n: (language: string) => ['ego', 'list', 'i18n', language] as const,
}

// EGO spec list query options with runtime validation
function createEGOSpecListQueryOptions() {
  return queryOptions({
    queryKey: egoListQueryKeys.spec(),
    queryFn: async () => {
      const module = await import('@static/data/egoSpecList.json')
      const result = EGOSpecListSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[ego specList] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

// EGO name list query options with runtime validation
function createEGONameListQueryOptions(language: string) {
  return queryOptions({
    queryKey: egoListQueryKeys.i18n(language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/egoNameList.json`)
      const result = EGONameListSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[ego nameList / ${language}] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads and validates EGO list data (spec list + name list)
 * Suspends while loading - wrap in Suspense boundary
 *
 * Returns spec map and name map separately for flexible consumption.
 *
 * @returns Validated EGO spec map and name map
 */
export function useEGOListData() {
  const { i18n } = useTranslation()

  const { data: spec } = useSuspenseQuery(createEGOSpecListQueryOptions())
  const { data: i18nData } = useSuspenseQuery(
    createEGONameListQueryOptions(i18n.language)
  )

  return {
    spec,
    i18n: i18nData,
  }
}

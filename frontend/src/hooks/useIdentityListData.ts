import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { IdentitySpecListSchema, IdentityNameListSchema } from '@/schemas'
import queryConfig from '@static/config/queryConfig.json'

// Query key factory for identity list data
export const identityListQueryKeys = {
  all: () => ['identity', 'list'] as const,
  spec: () => ['identity', 'list', 'spec'] as const,
  i18n: (language: string) => ['identity', 'list', 'i18n', language] as const,
}

// Identity spec list query options with runtime validation
function createIdentitySpecListQueryOptions() {
  return queryOptions({
    queryKey: identityListQueryKeys.spec(),
    queryFn: async () => {
      const module = await import('@static/data/identitySpecList.json')
      const result = IdentitySpecListSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[identity specList] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: queryConfig.staleTime.identity,
  })
}

// Identity name list query options with runtime validation
function createIdentityNameListQueryOptions(language: string) {
  return queryOptions({
    queryKey: identityListQueryKeys.i18n(language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/identityNameList.json`)
      const result = IdentityNameListSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[identity nameList / ${language}] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads and validates identity list data (spec list + name list)
 * Suspends while loading - wrap in Suspense boundary
 *
 * Returns spec map and name map separately for flexible consumption.
 *
 * @returns Validated identity spec map and name map
 */
export function useIdentityListData() {
  const { i18n } = useTranslation()

  const { data: spec } = useSuspenseQuery(createIdentitySpecListQueryOptions())
  const { data: i18nData } = useSuspenseQuery(
    createIdentityNameListQueryOptions(i18n.language)
  )

  return {
    spec,
    i18n: i18nData,
  }
}

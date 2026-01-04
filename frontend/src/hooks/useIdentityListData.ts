import { useSuspenseQuery, useQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { IdentitySpecListSchema, IdentityNameListSchema } from '@/schemas'

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
      const response = await fetch('/data/identitySpecList.json')
      if (!response.ok) throw new Error(`Failed to fetch identitySpecList: ${response.status}`)
      const data: unknown = await response.json()
      const result = IdentitySpecListSchema.safeParse(data)
      if (!result.success) {
        throw new Error(`[identity specList] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

// Identity name list query options with runtime validation
function createIdentityNameListQueryOptions(language: string) {
  return queryOptions({
    queryKey: identityListQueryKeys.i18n(language),
    queryFn: async () => {
      const response = await fetch(`/i18n/${language}/identityNameList.json`)
      if (!response.ok) throw new Error(`Failed to fetch identityNameList: ${response.status}`)
      const data: unknown = await response.json()
      const result = IdentityNameListSchema.safeParse(data)
      if (!result.success) {
        throw new Error(`[identity nameList / ${language}] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads Identity spec list only (no language dependency)
 * Suspends on initial load, but NOT on language change (key has no language)
 *
 * Use this in shell components that should stay stable during language change.
 *
 * @returns Validated Identity spec map
 */
export function useIdentityListSpec() {
  const { data: spec } = useSuspenseQuery(createIdentitySpecListQueryOptions())
  return spec
}

/**
 * Hook that loads and validates Identity name list only
 * Suspends while loading - wrap in Suspense boundary
 *
 * Use this in components wrapped in their own Suspense boundary
 * for granular loading states on language change.
 *
 * @returns Validated Identity name map
 */
export function useIdentityListI18n() {
  const { i18n } = useTranslation()
  const { data: i18nData } = useSuspenseQuery(
    createIdentityNameListQueryOptions(i18n.language)
  )
  return i18nData
}

// Empty name list constant for loading state
const EMPTY_NAME_LIST: Record<string, string> = {}

/**
 * Non-suspending version of useIdentityListI18n for list filtering.
 * Returns empty object while loading - name search won't match anything.
 * Use this in list components to prevent suspension during language change.
 *
 * @returns Identity name map (id -> name), empty object while loading
 */
export function useIdentityListI18nDeferred(): Record<string, string> {
  const { i18n } = useTranslation()
  const { data: i18nData } = useQuery(
    createIdentityNameListQueryOptions(i18n.language)
  )
  return i18nData ?? EMPTY_NAME_LIST
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
